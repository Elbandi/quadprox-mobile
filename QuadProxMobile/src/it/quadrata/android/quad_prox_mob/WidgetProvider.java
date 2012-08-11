package it.quadrata.android.quad_prox_mob;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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


public class WidgetProvider extends AppWidgetProvider {
	public static final String URI_SCHEME = "quadprox_mobile_widget";
	private static final String TAG = "it.quadrata.android.quad_prox_mob.widget_provider";
	@SuppressLint("UseSparseArrays")
	private static HashMap<Integer, Uri> uris = new HashMap<Integer, Uri>();

	@Override
	public void onUpdate(Context context,
						 AppWidgetManager appWidgetManager,
						 int[] appWidgetIds) {
		Log.i(TAG, "onUpdate method called");
		for (int widgetId : appWidgetIds) {
			new UpdateTask(context, appWidgetManager).execute(widgetId);
		}
	}
	
	@Override
	public void onDeleted(Context context, int[] appWidgetIds) {
		Log.i(TAG, "onDeleted method called");
		for (int i: appWidgetIds) {
			SharedPreferences widgetPrefs = 
					context.getSharedPreferences("WidgetPrefs_" + i, Context.MODE_PRIVATE);
			Log.i(TAG, "Removing preferences for widget instanse [" + i + "]");
			SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();
			widgetPrefsEditor.clear();
			widgetPrefsEditor.commit();
			cancelAlarmManager(context, i);
		}

		super.onDeleted(context, appWidgetIds);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
        // Protect against rogue update broadcasts (not really a security issue,
        // just filter bad broacasts out so subclasses are less likely to crash).
        String action = intent.getAction();
        
		Log.i(TAG, "onReive method called with action '" + action + "'");
		if (AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)) {
			//Check if there is a single widget ID.
			int widgetID = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 
					AppWidgetManager.INVALID_APPWIDGET_ID);
			Log.i(TAG, "EXTRA_APPWIDGET_ID: " + widgetID);
			//If there is a single ID, call our unUpdate implementation.
			if(widgetID != AppWidgetManager.INVALID_APPWIDGET_ID) {
				onUpdate(context, AppWidgetManager.getInstance(context),
						new int[]{widgetID});
			}
			else {
				super.onReceive(context, intent);
			}
        }
        else if (AppWidgetManager.ACTION_APPWIDGET_DELETED.equals(action)) {
            Bundle extras = intent.getExtras();
            if (extras != null && extras.containsKey(AppWidgetManager.EXTRA_APPWIDGET_ID)) {
                final int appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID);
                onDeleted(context, new int[] { appWidgetId });
            }
        }
        else if (AppWidgetManager.ACTION_APPWIDGET_ENABLED.equals(action)) {
        	super.onEnabled(context);
        }
        else if (AppWidgetManager.ACTION_APPWIDGET_DISABLED.equals(action)) {
        	super.onDisabled(context);
        }
    }

	protected void cancelAlarmManager(Context context, int widgetId) {
		AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		Intent intentUpdate = new Intent(context, WidgetProvider.class);
		intentUpdate.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		//Don't put the uri to cancel all the AlarmManager with action UPDATE_ONE.
		intentUpdate.setData(uris.get(widgetId));
		intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
		PendingIntent pendingIntentAlarm = PendingIntent.getBroadcast(
			context, 0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT);
		alarm.cancel(pendingIntentAlarm);
		Log.d("cancelAlarmManager", "Cancelled Alarm. Action URI = " + uris.get(widgetId));
		uris.remove(widgetId);
	}
	  
	public static void addUri(int id, Uri uri) {
		uris.put(Integer.valueOf(id), uri);
	}
	
	public static void updateUri(int id, Uri uri) {
		if (! uris.containsKey(Integer.valueOf(id)))
			uris.put(Integer.valueOf(id), uri);	
	}

    public static Intent get_ACTION_APPWIDGET_UPDATE_Intent(Context context, int widgetId) {
        ComponentName thisAppWidget = new ComponentName(context.getPackageName(),
					WidgetProvider.class.getName());
        int[] appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(thisAppWidget);
        Intent intent = new Intent(context, WidgetProvider.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        
        Uri uriData = Uri.withAppendedPath(
						Uri.parse(WidgetProvider.URI_SCHEME + "://widget/id/"), String.valueOf(widgetId));
        intent.setData(uriData);
        Log.i(TAG, "Setting appWidget scheme [" + uriData.toString() + "]");
        
        return intent;
    }

	private class UpdateTask extends AsyncTask<Integer, Void, Void> {

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
		private AppWidgetManager appWidgetManager;
		private int widgetId;
		
		public UpdateTask(Context context, AppWidgetManager appWidgetManager) {
			this.context = context;
			this.appWidgetManager = appWidgetManager;
		}
		
		@Override
		protected Void doInBackground(Integer... params) {
			String cluster;
			
			RemoteViews updateViews = new RemoteViews(
					context.getPackageName(), R.layout.widget_layout);
			widgetId = params[0];
			
			updateViews.removeAllViews(R.id.nodeList);
			Log.i(TAG, "Called with appWidgetId = " + widgetId);
			
	        // Retrieving of widget preferences
			Map<String, String> prefs = WidgetPrefsActivity.getPrefs(context, widgetId);
			
	        // Retrieving of login preferences
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
				cluster = clusterInfo.optString("name");
				updateViews.setTextViewText(R.id.hostInfo, cluster);

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
					item.cluster = cluster;
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
					item.index = i;
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
					item.cpuUsage = Double.valueOf(node_cpu_usage);
					item.memUsage = (Double.valueOf(node_mem) * 100) /
							Double.valueOf(node_max_mem);

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
					Log.i(TAG, item.toString());
					RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_row_layout);
					
					// ... Set Text on text views in "v" for the current object
					float fontSize = Integer.parseInt(prefs.get("fontSize"));
					Log.i(TAG, "Setting FontSize = " + fontSize + "sp");
					v.setTextViewText(R.id.node, item.node);
					v.setFloat(R.id.node, "setTextSize", fontSize);
					v.setTextViewText(R.id.cpu, item.cpu);
					v.setFloat(R.id.cpu, "setTextSize", fontSize);
					v.setTextViewText(R.id.memory, item.memory);
					v.setFloat(R.id.memory, "setTextSize", fontSize);
					v.setTextViewText(R.id.status, item.status);
					v.setFloat(R.id.status, "setTextSize", fontSize);

					Intent vmListIntent = new Intent(context, VMListActivity.class);
					// Putting VM data into the intent for VM stats activity
                    long id = WidgetPrefsActivity.getNextId(context, widgetId);
                    Uri uri = Uri.withAppendedPath(Uri.parse(WidgetProvider.URI_SCHEME +
                            "://widget/id/"), String.valueOf(widgetId) + "/" + String.valueOf(id));
                    Log.i(TAG, "Setting URI: " + uri.toString());
					vmListIntent.setData(uri);

					vmListIntent.putExtra("server", server);
					vmListIntent.putExtra("ticket", ticket);
					vmListIntent.putExtra("token", token);
					vmListIntent.putExtra("node", item.node);
					vmListIntent.putExtra("node_index", i);
					vmListIntent.putExtra("node_vers", item.node_vers);
					PendingIntent pendingIntent = PendingIntent.getActivity(
									context, 0, vmListIntent, 0);//Intent.FLAG_ACTIVITY_NEW_TASK);
					v.setOnClickPendingIntent(R.id.widgetNodeRow, pendingIntent);
					
					// Add new view to the linear layout in the widget
					updateViews.addView(R.id.nodeList, v);
					
					checkNotifications(item, prefs);
				}

				Uri uriData = Uri.withAppendedPath(
						Uri.parse(WidgetProvider.URI_SCHEME + "://widget/id/"), String.valueOf(widgetId));

				// Add onClickListener for cluster
				Intent nodeListIntent = new Intent(context, NodeListActivity.class);
				nodeListIntent.setAction(Intent.ACTION_VIEW);
				nodeListIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				nodeListIntent.setData(uriData);
				Log.i(TAG, "Adding onClickListener for NodeListActivity. Setting appWidgetId = " + widgetId);
				PendingIntent pendingIntentCluster = 
						PendingIntent.getActivity(context, 0, nodeListIntent, 0);//Intent.FLAG_ACTIVITY_NEW_TASK);
				updateViews.setOnClickPendingIntent(R.id.hostInfo, pendingIntentCluster);

				// Add onClickListener for refresh
				Intent refreshIntent = WidgetProvider.get_ACTION_APPWIDGET_UPDATE_Intent(context, widgetId);
				Log.i(TAG, "Setting up pending broadcast event for widget " + widgetId);
				PendingIntent pendingIntentUpdate = 
						PendingIntent.getBroadcast(context, 0, refreshIntent, 0/*Intent.FLAG_RECEIVER_REPLACE_PENDING*/);
				updateViews.setOnClickPendingIntent(R.id.widgetRefresh, pendingIntentUpdate);
				
				// Add onClickListener for preferences
				Intent prefsIntent = new Intent(context, WidgetPrefsActivity.class);
				prefsIntent.setAction(Intent.ACTION_VIEW);
				prefsIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				prefsIntent.setData(uriData);
				Log.i(TAG, "Adding onClickListener for WidgetPrefsActivity. Setting appWidgetId = " + widgetId);
				PendingIntent pendingIntentPrefs = 
						PendingIntent.getActivity(context, 0, prefsIntent, 0);//Intent.FLAG_ACTIVITY_NEW_TASK);
				updateViews.setOnClickPendingIntent(R.id.widgetConfig, pendingIntentPrefs);

				updateUri(widgetId, uriData);
				
				//updateViews
                appWidgetManager.updateAppWidget(widgetId, updateViews);
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
		
		private void checkNotifications(NodeItem item,	Map<String, String> prefs) {
			String notifyCpu = prefs.get("notifyCpu");
			String notifyMem = prefs.get("notifyMem");
			String cpuLimit = prefs.get("cpuLimit");
			String memLimit = prefs.get("memLimit");
			String notifyChange = prefs.get("notifyChange");
			
			if (notifyCpu.equals("true") && cpuLimit != null) {
				if (item.cpuUsage > Integer.valueOf(cpuLimit)) {
					createNotification(item, "CPU usage [" + cpu_dec_form.format(item.cpuUsage) +
					"%] exceeds" + " [" + cpuLimit + "%]", (item.index * 10) + 1);
					Log.i(TAG, "CPU Limit: " + item.node + " -> " + item.cpuUsage + " > " + cpuLimit);
				}
			}
			if (notifyMem.equals("true") && memLimit != null) {
				if (item.memUsage > Integer.valueOf(memLimit)) {
					createNotification(item, "Memory usage [" + cpu_dec_form.format(item.memUsage) +
					"%] exceeds" + " [" + memLimit + "%]", (item.index * 10) + 2);
					Log.i(TAG, "Mem Limit: " + item.node + " -> " + item.memUsage + " > " + memLimit);
				}
			}
			if (notifyChange.equals("true")) {
				int last = 0, now = 0;
				String status = WidgetPrefsActivity.getStatus(context, widgetId, item.node);
				String current = item.status.substring(0, item.status.indexOf("/"));
				if (status != null) {
					try {
						last = Integer.parseInt(status);
						now = Integer.parseInt(current);
						if (last != now) {
							createNotification(item, "Status change: last [" + last + "] -> now [" + now + "]",
									(item.index * 10) + 3);
							Log.i(TAG, "Status change: Running last check [" + last + "] -> now [" + now + "]");
						}
					} catch (NumberFormatException e) {
						Log.e(TAG, e.getMessage());
					}
				}
				if (status == null || last != now)
					WidgetPrefsActivity.setStatus(context, widgetId, item.node, current);
			}
		}
		
		/*
		 * We support API level < 11 so use backwards
		 * compatible notification interface
		 */
		@SuppressWarnings("deprecation")
		public void createNotification(NodeItem item, String text, int id) {
			NotificationManager notificationManager = (NotificationManager) 
					context.getSystemService(Context.NOTIFICATION_SERVICE);
			Notification notification = new Notification(R.drawable.qpm_launcher,
					item.cluster + ":" + item.node + ": Alarm", System.currentTimeMillis());
			notification.flags |= Notification.FLAG_AUTO_CANCEL;
			
			Intent intent = new Intent(context, VMListActivity.class);
			// Putting VM data into the intent for VM stats activity
			intent.setData(Uri.withAppendedPath(
					Uri.parse(WidgetProvider.URI_SCHEME + "://widget/id/"), 
						String.valueOf(widgetId) + "/" + item.index));
			intent.putExtra("server", server);
			intent.putExtra("ticket", ticket);
			intent.putExtra("token", token);
			intent.putExtra("node", item.node);
			intent.putExtra("node_index", item.index);
			intent.putExtra("node_vers", item.node_vers);
			PendingIntent activity = PendingIntent.getActivity(context, 0, intent, 0);
			notification.setLatestEventInfo(context, item.cluster + ":" + item.node + ": Alarm",
					text, activity);
			notification.number += 1;
			notificationManager.notify(id, notification);
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
			public Double cpuUsage;
			public Double memUsage;
			public int index;
			public String cluster;
			
			@Override
			public String toString() {
				return node + ":" + cpu + ":" + memory + ":" + status;
			}
		}

	}

}
