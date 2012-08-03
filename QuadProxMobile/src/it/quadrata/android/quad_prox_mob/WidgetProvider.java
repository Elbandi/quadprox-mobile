package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.RemoteViews;


public class WidgetProvider extends AppWidgetProvider {
	private static final String TAG = "it.quadrata.android.quad_prox_mob.widget_provider";
	public static String WIDGET_UPDATE_ACTION = "WIDGET_UPDATE";


	@Override
	public void onUpdate(Context context,
						 AppWidgetManager appWidgetManager,
						 int[] appWidgetIds) {
		Log.w(TAG, "onUpdate method called");

		new UpdateTask(context).execute((Void[]) null);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		super.onReceive(context, intent);
		if (intent.getAction().equals(WIDGET_UPDATE_ACTION)) {
			Log.w(TAG, "onReive method called");

			new UpdateTask(context).execute((Void[]) null);
		}
	}

	private class UpdateTask extends AsyncTask<Void, Void, Void> {

		// Authentication credentials
		private String server;
		private String username;
		private String realm;
		private String password;
		private String ticket;
		private String token;

		// Host info
		private String version;
		private String release;

		// Node info
		private double node_cpu_usage_double;
		private String node_cpu_usage;
		private int node_max_cpu;
		private double node_mem_double;
		private double node_max_mem_double;
		private String node_mem;
		private String node_max_mem;
		final DecimalFormat cpu_dec_form = new DecimalFormat("0.0");

		private Context context;
		
		public UpdateTask(Context context) {
			this.context = context;
		}
		
		@Override
		protected Void doInBackground(Void... params) {
			RemoteViews updateViews = new RemoteViews(
					context.getPackageName(), R.layout.widget_layout);
			
			updateViews.removeAllViews(R.id.nodeList);
			Log.i(getClass().getSimpleName(), "Called");
			
	        // Retrieving of login preferences, otherwise start login view
			SharedPreferences authPref = context.getSharedPreferences("AuthPref",
					Context.MODE_PRIVATE);
			server = authPref.getString("server", "");
			username = authPref.getString("username", "");
			realm = authPref.getString("realm", "");
			password = authPref.getString("password", "");

			try {
				if (isOnline() == false) {
					throw new IOException();
				}
				ProxmoxCustomApp httpApp = (ProxmoxCustomApp) context.getApplicationContext();
				HttpClient serverHttpClient = httpApp.getHttpClient();

				// Proxmox ticket request
				HttpPost authRequest = new HttpPost(server
						+ "/api2/json/access/ticket");
				List<NameValuePair> authParameters = new ArrayList<NameValuePair>();
				authParameters.add(new BasicNameValuePair("username",
						username + "@" + realm));
				authParameters.add(new BasicNameValuePair("password",
						password));
				HttpEntity authEntity = new UrlEncodedFormEntity(
						authParameters);
				authRequest.setEntity(authEntity);
				String authResponse = serverHttpClient.execute(authRequest,
						serverResponseHandler);
				// Ticket and token extraction from authentication
				// json string
				JSONObject authObject = new JSONObject(authResponse);
				JSONObject data = authObject.getJSONObject("data");
				ticket = data.getString("ticket");
				token = data.getString("CSRFPreventionToken");

				// Cluster info request				
				HttpGet clusterRequest = new HttpGet(server
						+ "/api2/json/cluster/status");
				clusterRequest.addHeader("Cookie", "PVEAuthCookie="
						+ ticket);
				String clusterResponse = serverHttpClient.execute(
						clusterRequest, serverResponseHandler);
				JSONObject clusterObject = new JSONObject(clusterResponse);
				JSONArray clusterDataArray = clusterObject
						.getJSONArray("data");
				JSONObject clusterInfo =  (JSONObject) clusterDataArray.get(0);
				updateViews.setTextViewText(R.id.hostInfo, clusterInfo.optString("name"));

				HttpGet versionRequest = new HttpGet(server
						+ "/api2/json/version");
				versionRequest.addHeader("Cookie", "PVEAuthCookie="
						+ ticket);
				String versionResponse = serverHttpClient.execute(
						versionRequest, serverResponseHandler);
				JSONObject versionObject = new JSONObject(versionResponse);
				JSONObject versionDataObject = versionObject
						.getJSONObject("data");
				version = versionDataObject.getString("version");
				release = versionDataObject.getString("release");
				updateViews.setTextViewText(R.id.hostVers, "Proxmox VE " + version + "-" + release);

				// Nodes list request
				HttpGet nodesRequest = new HttpGet(server
						+ "/api2/json/nodes");
				nodesRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
				String nodesResponse = serverHttpClient.execute(
						nodesRequest, serverResponseHandler);
				JSONObject nodesObject = new JSONObject(nodesResponse);
				JSONArray nodesArray = nodesObject.getJSONArray("data");
				int nodesArrayLength = nodesArray.length();

				// Nodes list items creation
				JSONObject singleNodeObject = new JSONObject();
				for (int i = 0; i < nodesArrayLength; i++) {
					singleNodeObject = nodesArray.getJSONObject(i);
					final NodeItem item = new NodeItem();
					item.node = singleNodeObject.optString("node");
					// Status request
					HttpGet qemuRequest = new HttpGet(server
							+ "/api2/json/nodes/" + item.node + "/qemu");
					qemuRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String qemuResponse = serverHttpClient.execute(
							qemuRequest, serverResponseHandler);
					JSONObject qemuObject = new JSONObject(qemuResponse);
					JSONArray qemuArray = qemuObject.getJSONArray("data");
					int qemuArrayLength = qemuArray.length();
					int running = 0;
					for (int q = 0; q < qemuArrayLength; q++) {
						JSONObject qemu = qemuArray.getJSONObject(q);
						String status = qemu.optString("status");
						if (status.equals("running"))
							running++;
					}
					HttpGet vzRequest = new HttpGet(server
							+ "/api2/json/nodes/" + item.node + "/openvz");
					vzRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String vzResponse = serverHttpClient.execute(
							vzRequest, serverResponseHandler);
					JSONObject vzObject = new JSONObject(vzResponse);
					JSONArray vzArray = vzObject.getJSONArray("data");
					int vzArrayLength = vzArray.length();
					for (int v = 0; v < vzArrayLength; v++) {
						JSONObject vz = vzArray.getJSONObject(v);
						String status = vz.optString("status");
						if (status.equals("running"))
							running++;
					}
					item.status = running + "/" + (qemuArrayLength + vzArrayLength) + " up";

					node_cpu_usage_double = singleNodeObject.optDouble("cpu", 0) * 100;
					node_cpu_usage = cpu_dec_form.format(node_cpu_usage_double);
					node_max_cpu = singleNodeObject.optInt("maxcpu", 0);
					node_mem_double = singleNodeObject.optDouble("mem", 0);
					node_max_mem_double = singleNodeObject.optDouble("maxmem", 0);
					node_mem = cpu_dec_form
							.format(node_mem_double / 1073741824);
					node_max_mem = cpu_dec_form
							.format(node_max_mem_double / 1073741824);
					item.cpu = node_cpu_usage + "% (" + node_max_cpu + " cpu)";
					item.memory = node_mem + "GB of " + node_max_mem + "GB";

					HttpGet nodesVersRequest = new HttpGet(server
							+ "/api2/json/nodes/" + item.node
							+ "/version");
					nodesVersRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					String nodesVersResponse = serverHttpClient.execute(
							nodesVersRequest, serverResponseHandler);
					JSONObject nodesVersObject = new JSONObject(
							nodesVersResponse);
					JSONObject nodesVersDataObject = nodesVersObject
							.getJSONObject("data");
					item.node_vers = (nodesVersDataObject
							.getString("version") + "-" + nodesVersDataObject
							.getString("release"));
					
					// Create remote view for the object to add to linear layout
					Log.i(getClass().getSimpleName(), item.toString());
					RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_row_layout);
					
					// ... Set Text on text views in "v" for the current object
					v.setTextViewText(R.id.node, item.node);
					v.setTextViewText(R.id.cpu, item.cpu);
					v.setTextViewText(R.id.memory, item.memory);
					v.setTextViewText(R.id.status, item.status);
					
					// Add new view to the linear layout in the widget
					updateViews.addView(R.id.nodeList, v);

					Intent vmListIntent = new Intent(context, VMListActivity.class);
					// Putting VM data into the intent for VM stats activity
					vmListIntent.setAction("NODE_ROW_CLICK_ACTION_" + i);
					vmListIntent.putExtra("server", server);
					vmListIntent.putExtra("ticket", ticket);
					vmListIntent.putExtra("token", token);
					vmListIntent.putExtra("node", item.node);
					vmListIntent.putExtra("node_index", i);
					vmListIntent.putExtra("node_vers", item.node_vers);
					PendingIntent pendingIntent = 
							PendingIntent.getActivity(context, 0, vmListIntent, 0);
					v.setOnClickPendingIntent(R.id.widgetNodeRow, pendingIntent);

				}

				Intent nodeListIntent = new Intent(context, NodeListActivity.class);
				nodeListIntent.setAction("CLUSTER_CLICK_ACTION");
				PendingIntent pendingIntentCluster = 
						PendingIntent.getActivity(context, 0, nodeListIntent, 0);
				updateViews.setOnClickPendingIntent(R.id.hostInfo, pendingIntentCluster);
				
				Intent intent = new Intent(context, WidgetProvider.class);
				intent.setAction(WidgetProvider.WIDGET_UPDATE_ACTION);
				PendingIntent pendingIntentUpdate = 
						PendingIntent.getBroadcast(context, 0, intent, 0);
				updateViews.setOnClickPendingIntent(R.id.widgetUpdate, pendingIntentUpdate);
				
				ComponentName thisWidget = new ComponentName(context, WidgetProvider.class);
                AppWidgetManager manager = AppWidgetManager.getInstance(context);
                manager.updateAppWidget(thisWidget, updateViews);
			} catch (JSONException e) {
				if (e.getMessage() != null) {
					Log.e(e.getClass().getName(), e.getMessage());
				} else {
					Log.e(e.getClass().getName(), "null");
				}
			} catch (IOException e) {
				if (e.getMessage() != null) {
					Log.e(e.getClass().getName(), e.getMessage());
				} else {
					Log.e(e.getClass().getName(), "null");
				}
			} catch (RuntimeException e) {
				if (e.getMessage() != null) {
					Log.e(e.getClass().getName(), e.getMessage());
				} else {
					Log.e(e.getClass().getName(), "null");
				}
			}
			return null;
		}

		private ResponseHandler<String> serverResponseHandler = new ResponseHandler<String>() {

			@Override
			public String handleResponse(HttpResponse response)
					throws ClientProtocolException, IOException {
				HttpEntity entity = response.getEntity();
				String result = EntityUtils.toString(entity);

				return result;
			}

		};

		public boolean isOnline() {
			ConnectivityManager connMgr = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
			NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
			return (networkInfo != null && networkInfo.isConnected());
		}

		private class NodeItem {
			public String node;
			public String cpu;
			public String memory;
			public String status;
			public String node_vers;
			
			@Override
			public String toString() {
				return node + ":" + cpu + ":" + memory + ":" + status;
			}
		}

	}

}
