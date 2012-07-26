package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class ClusterLogActivity extends Activity {

	// Authentication credentials
	private static String server;
	private static String ticket;

	// Cluster info
	// private static String cluster;

	// Log variables
	private static int errors;
	private static String status;

	// Epoch time variables
	private long timeStart;
	private long timeStop;

	// Task info
	private static String node;
	private static String upid;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log_list_layout);

		// Cluster data retrieving
		Intent logListIntent = ClusterLogActivity.this.getIntent();
		server = logListIntent.getStringExtra("server");
		ticket = logListIntent.getStringExtra("ticket");

		buildLogList();
	}

	private void buildLogList() {
		// Logs header views
		// final TextView clusterInfoText = (TextView)
		// findViewById(R.id.clusterInfo);
		final TextView taskErrorsText = (TextView) findViewById(R.id.taskErrors);
		errors = 0;

		// Tasks list view and adapter
		ListView taskListView = (ListView) findViewById(R.id.taskList);
		final ArrayAdapter<TaskItem> taskArrayAdapter = new ArrayAdapter<TaskItem>(
				this, R.layout.log_row_layout, R.id.logRow) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				return getTaskRowHolder(position, convertView, parent);
			}

			public View getTaskRowHolder(int position, View convertView,
					ViewGroup parent) {
				TaskRowHolder rowHolder = null;
				if (convertView == null) {
					LayoutInflater taskInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = taskInflater.inflate(R.layout.log_row_layout,
							null);
					rowHolder = new TaskRowHolder();
					rowHolder.taskRowStart = (TextView) convertView
							.findViewById(R.id.taskStartTime);
					rowHolder.taskRowStop = (TextView) convertView
							.findViewById(R.id.taskStopTime);
					rowHolder.taskRowType = (TextView) convertView
							.findViewById(R.id.taskType);
					rowHolder.taskRowVm = (TextView) convertView
							.findViewById(R.id.taskVm);
					rowHolder.taskRowUser = (TextView) convertView
							.findViewById(R.id.taskUser);
					rowHolder.taskRowBkg = (ImageView) convertView
							.findViewById(R.id.taskColor);
					convertView.setTag(rowHolder);
				} else {
					rowHolder = (TaskRowHolder) convertView.getTag();
				}
				TaskItem item = getItem(position);
				rowHolder.taskRowStart.setText(item.task_start);
				rowHolder.taskRowStop.setText(item.task_stop);
				rowHolder.taskRowType.setText(item.task_type);
				rowHolder.taskRowVm.setText(item.task_id + ", "
						+ item.task_node);
				rowHolder.taskRowUser.setText(item.task_user);
				rowHolder.taskRowBkg.setBackgroundColor(item.task_bkg);
				return convertView;
			}
		};

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient logHttpClient = httpApp.getHttpClient();

					// Log list request
					HttpGet logRequest = new HttpGet(server
							+ "/api2/json/cluster/tasks");
					logRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String logResponse = logHttpClient.execute(logRequest,
							serverResponseHandler);
					JSONObject logObject = new JSONObject(logResponse);
					JSONArray logJsonArray = logObject.getJSONArray("data");
					final int logJsonArrayLenght = logJsonArray.length();
					// cluster = server.substring(8, server.length() - 5);
					// clusterInfoText.post(new Runnable() {
					// @Override
					// public void run() {
					// clusterInfoText.setText(cluster);
					// }
					// });

					// Log list items creation
					JSONObject singleTaskObject = new JSONObject();
					for (int i = 0; i <= (logJsonArrayLenght - 1); i++) {
						singleTaskObject = logJsonArray.getJSONObject(i);
						final TaskItem item = new TaskItem();
						timeStart = singleTaskObject.getLong("starttime");
						item.task_start = new SimpleDateFormat(
								"MMM dd HH:mm:ss").format(new Date(
								timeStart * 1000));
						timeStop = singleTaskObject.optLong("endtime");
						if (timeStop == 0) {
							item.task_stop = "Running";
						} else {
							item.task_stop = new SimpleDateFormat(
									"MMM dd HH:mm:ss").format(new Date(
									timeStop * 1000));
						}
						item.task_type = singleTaskObject.getString("type");
						item.task_id = singleTaskObject.optString("id");
						if (item.task_id.equals("")) {
							item.task_id = "###";
						}
						item.task_node = singleTaskObject.getString("node");
						item.task_user = singleTaskObject.getString("user");
						status = singleTaskObject.optString("status");
						if (status.equals("OK")) {
							item.task_bkg = Color.parseColor("#96FF98");
						} else {
							if (status == "") {
								item.task_bkg = Color.parseColor("#FDFF96");
							} else {
								errors = errors + 1;
								item.task_bkg = Color.parseColor("#FF9696");
							}
						}
						item.task_upid = singleTaskObject.getString("upid");
						ClusterLogActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								taskArrayAdapter.add(item);
							}
						});
					}
					taskErrorsText.post(new Runnable() {
						@Override
						public void run() {
							taskErrorsText.setText(Integer.toString(errors));
						}
					});
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					showNoConnDialog();
				}
			}
		}).start();

		taskListView.setAdapter(taskArrayAdapter);

		taskListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				TaskItem item = (TaskItem) parent.getItemAtPosition(position);
				node = item.task_node;
				upid = item.task_upid;
				Intent logStatsIntent = new Intent(ClusterLogActivity.this,
						LogStatsActivity.class);
				logStatsIntent.putExtra("server", server);
				logStatsIntent.putExtra("ticket", ticket);
				logStatsIntent.putExtra("node", node);
				logStatsIntent.putExtra("upid", upid);
				startActivity(logStatsIntent);
			}
		});
	}

	private static class TaskItem {
		public String task_start;
		public String task_stop;
		public String task_type;
		public String task_id;
		public String task_node;
		public String task_user;
		public String task_upid;
		public int task_bkg;
	}

	private static class TaskRowHolder {
		public TextView taskRowStart;
		public TextView taskRowStop;
		public TextView taskRowType;
		public TextView taskRowVm;
		public TextView taskRowUser;
		public ImageView taskRowBkg;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater nodesMenu_inflater = getMenuInflater();
		nodesMenu_inflater.inflate(R.menu.log_list_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.updateLogsPref:
			buildLogList();
			return true;
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

	private void showNoConnDialog() {
		ClusterLogActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						ClusterLogActivity.this);
				builder.setTitle("Unable to connect");
				builder.setMessage("Do you want to retry?");
				builder.setCancelable(false);
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								buildLogList();
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								ClusterLogActivity.this.finish();
							}
						});
				AlertDialog alertDialog = builder.create();
				alertDialog.show();
			}
		});
	}

	public boolean isOnline() {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		return (networkInfo != null && networkInfo.isConnected() && !networkInfo
				.isFailover());
	}

}