package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class NodeListActivity extends Activity {

	// Authentication credentials
	private static String server;
	private static String username;
	private static String realm;
	private static String password;
	private static String ticket;
	private static String token;

	// Host info
	private static String cluster;
	private static String version;
	private static String release;

	// Node info
	private static String node;
	private static String node_vers;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.nodes_list_layout);

		// Retrieving of login preferences, otherwise start login view
		final SharedPreferences authPref = getSharedPreferences("AuthPref",
				Context.MODE_PRIVATE);
		server = authPref.getString("server", null);
		username = authPref.getString("username", null);
		realm = authPref.getString("realm", null);
		password = authPref.getString("password", null);
		if ((server == null) || (username == null) || (password == null)) {
			Intent loginIntent = new Intent(NodeListActivity.this,
					AuthActivity.class);
			startActivityForResult(loginIntent, 0);
		} else {
			buildNodeList();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		if ((requestCode == 0) && (resultCode == RESULT_OK)) {
			server = data.getStringExtra("server");
			username = data.getStringExtra("username");
			realm = data.getStringExtra("realm");
			password = data.getStringExtra("password");
			buildNodeList();
		}
	}

	private void buildNodeList() {
		// Cluster header views
		final TextView hostInfo = (TextView) findViewById(R.id.hostInfo);
		final TextView hostVers = (TextView) findViewById(R.id.hostVers);
		final TextView hostNodes = (TextView) findViewById(R.id.hostNodes);

		// Nodes list view and adapter
		ListView nodeListView = (ListView) findViewById(R.id.nodeList);
		final ArrayAdapter<NodeItem> nodeArrayAdapter = new ArrayAdapter<NodeItem>(
				this, R.layout.node_row_layout, R.id.nodeRow) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				return getNodeRowHolder(position, convertView, parent);
			}

			public View getNodeRowHolder(int position, View convertView,
					ViewGroup parent) {
				NodeRowHolder rowHolder = null;
				if (convertView == null) {
					LayoutInflater nodeInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = nodeInflater.inflate(
							R.layout.node_row_layout, null);
					rowHolder = new NodeRowHolder();
					rowHolder.nodeRowName = (TextView) convertView
							.findViewById(R.id.nodeRowName);
					rowHolder.nodeRowVers = (TextView) convertView
							.findViewById(R.id.nodeRowVers);
					convertView.setTag(rowHolder);
				} else {
					rowHolder = (NodeRowHolder) convertView.getTag();
				}
				NodeItem item = getItem(position);
				rowHolder.nodeRowName.setText(item.node_name);
				rowHolder.nodeRowVers.setText("Proxmox VE " + item.node_vers);
				return convertView;
			}
		};

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new IOException();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
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
					JSONObject clusterInfo = (JSONObject) clusterDataArray
							.get(0);
					cluster = clusterInfo.optString("name");
					hostInfo.post(new Runnable() {
						@Override
						public void run() {
							hostInfo.setText(cluster);
						}
					});

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
					hostVers.post(new Runnable() {
						@Override
						public void run() {
							hostVers.setText("Proxmox VE " + version + "-"
									+ release);
						}
					});

					// Nodes list request
					HttpGet nodesRequest = new HttpGet(server
							+ "/api2/json/nodes");
					nodesRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String nodesResponse = serverHttpClient.execute(
							nodesRequest, serverResponseHandler);
					JSONObject nodesObject = new JSONObject(nodesResponse);
					JSONArray nodesArray = nodesObject.getJSONArray("data");
					final int nodesArrayLength = nodesArray.length();
					hostNodes.post(new Runnable() {
						@Override
						public void run() {
							hostNodes.setText(Integer
									.toString(nodesArrayLength));
						}
					});

					// Nodes list items creation
					JSONObject singleNodeObject = new JSONObject();
					for (int i = 0; i <= (nodesArrayLength - 1); i++) {
						singleNodeObject = nodesArray.getJSONObject(i);
						final NodeItem item = new NodeItem();
						item.node_name = singleNodeObject.getString("node");
						HttpGet nodesVersRequest = new HttpGet(server
								+ "/api2/json/nodes/" + item.node_name
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
						NodeListActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								nodeArrayAdapter.add(item);
							}
						});
					}
				} catch (JSONException e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "null");
					}
					showWrongDataDialog();
				} catch (IOException e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "null");
					}
					showConnErrorDialog();
				} catch (RuntimeException e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "null");
					}
					showWrongServerDialog();
				}
			}
		}).start();

		nodeListView.setAdapter(nodeArrayAdapter);

		nodeListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				NodeItem item = (NodeItem) parent.getItemAtPosition(position);
				node = item.node_name;
				node_vers = item.node_vers;
				Intent vmListIntent = new Intent(NodeListActivity.this,
						VMListActivity.class);
				// Putting VM data into the intent for VM stats activity
				vmListIntent.putExtra("server", server);
				vmListIntent.putExtra("ticket", ticket);
				vmListIntent.putExtra("token", token);
				vmListIntent.putExtra("node", node);
				vmListIntent.putExtra("node_index", position);
				vmListIntent.putExtra("node_vers", node_vers);
				startActivity(vmListIntent);
			}
		});
	}

	private static class NodeItem {
		public String node_name;
		public String node_vers;
	}

	private static class NodeRowHolder {
		public TextView nodeRowName;
		public TextView nodeRowVers;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater nodesMenu_inflater = getMenuInflater();
		nodesMenu_inflater.inflate(R.menu.nodes_list_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.authPref:
			Intent loginIntent = new Intent(NodeListActivity.this,
					AuthActivity.class);
			startActivityForResult(loginIntent, 0);
			return true;
		case R.id.updateNodesPref:
			buildNodeList();
			return true;
		case R.id.logPref:
			Intent logIntent = new Intent(NodeListActivity.this,
					ClusterLogActivity.class);
			logIntent.putExtra("server", server);
			logIntent.putExtra("ticket", ticket);
			logIntent.putExtra("logHost", "");
			startActivity(logIntent);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private final ResponseHandler<String> serverResponseHandler = new ResponseHandler<String>() {

		@Override
		public String handleResponse(HttpResponse response)
				throws ClientProtocolException, IOException {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);

			return result;
		}

	};

	private void showWrongServerDialog() {
		NodeListActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						NodeListActivity.this);
				builder.setTitle("Invalid address");
				builder.setMessage("Wrong server address. \nDo you want to correct it?");
				builder.setCancelable(false);
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								Intent loginIntent = new Intent(
										NodeListActivity.this,
										AuthActivity.class);
								startActivityForResult(loginIntent, 0);
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								NodeListActivity.this.finish();
							}
						});
				AlertDialog alertDialog = builder.create();
				if (!isFinishing()) {
					alertDialog.show();
				}
			}
		});
	}

	private void showWrongDataDialog() {
		NodeListActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						NodeListActivity.this);
				builder.setTitle("Authentication error");
				builder.setMessage("Unable to authenticate. \nDo you want to check authentication data?");
				builder.setCancelable(false);
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								Intent loginIntent = new Intent(
										NodeListActivity.this,
										AuthActivity.class);
								startActivityForResult(loginIntent, 0);
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								NodeListActivity.this.finish();
							}
						});
				AlertDialog alertDialog = builder.create();
				if (!isFinishing()) {
					alertDialog.show();
				}
			}
		});
	}

	private void showConnErrorDialog() {
		NodeListActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						NodeListActivity.this);
				builder.setTitle("Connection error");
				builder.setMessage("Unable to connect. \nDo you want to retry or review server address?");
				builder.setCancelable(false);
				builder.setNeutralButton("Review",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								Intent loginIntent = new Intent(
										NodeListActivity.this,
										AuthActivity.class);
								startActivityForResult(loginIntent, 0);
							}
						});
				builder.setPositiveButton("Retry",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								buildNodeList();
							}
						});
				builder.setNegativeButton("Quit",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								NodeListActivity.this.finish();
							}
						});
				AlertDialog alertDialog = builder.create();
				if (!isFinishing()) {
					alertDialog.show();
				}
			}
		});
	}

	public boolean isOnline() {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		return (networkInfo != null && networkInfo.isConnected());
	}

}