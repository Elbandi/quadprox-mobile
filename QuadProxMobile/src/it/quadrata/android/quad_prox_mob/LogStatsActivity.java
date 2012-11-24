package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
import java.net.URI;
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
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.TextView;

public class LogStatsActivity extends Activity {

	// Authentication credentials
	private static String server;
	private static String ticket;

	// Task info
	private static String id;
	private static String type;
	private static String node;
	private static String status;
	private static String user;
	private static String start;
	private static String upid;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.log_stats_layout);

		// Task info retrieving
		Intent logStatsIntent = LogStatsActivity.this.getIntent();
		server = logStatsIntent.getStringExtra("server");
		ticket = logStatsIntent.getStringExtra("ticket");
		node = logStatsIntent.getStringExtra("node");
		upid = logStatsIntent.getStringExtra("upid");

		updateTaskStats();
	}

	private void updateTaskStats() {
		final TextView taskNameText = (TextView) findViewById(R.id.taskName);
		final TextView taskNodeText = (TextView) findViewById(R.id.taskNode);
		final TextView taskStatusText = (TextView) findViewById(R.id.taskStatus);
		final TextView taskUserText = (TextView) findViewById(R.id.taskUser);
		final TextView taskStartText = (TextView) findViewById(R.id.taskStart);
		final TextView taskProgressText = (TextView) findViewById(R.id.taskProgress);
		taskProgressText.setText("");

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient taskHttpClient = httpApp.getHttpClient();

					// Task progress request
					HttpGet taskProgressRequest = new HttpGet();
					URI taskProgressUri = new URI(server + "/api2/json/nodes/"
							+ node + "/tasks/" + upid + "/log");
					taskProgressRequest.setURI(taskProgressUri);
					taskProgressRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					String progressJson = taskHttpClient.execute(
							taskProgressRequest, taskResponseHandler);
					JSONObject progressObject = new JSONObject(progressJson);
					JSONArray progressArray = progressObject
							.getJSONArray("data");
					final int progressArrayLenght = progressArray.length();
					JSONObject progressRowObject = new JSONObject();
					for (int i = 0; i <= (progressArrayLenght - 1); i++) {
						progressRowObject = progressArray.getJSONObject(i);
						final String progressRow = progressRowObject
								.getString("t");
						taskProgressText.post(new Runnable() {
							@Override
							public void run() {
								taskProgressText.append(progressRow + "\n\n");
							}
						});
					}

					// Task status request
					HttpGet taskStatusRequest = new HttpGet();
					URI taskStatusUri = new URI(server + "/api2/json/nodes/"
							+ node + "/tasks/" + upid + "/status");
					taskStatusRequest.setURI(taskStatusUri);
					taskStatusRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					String statusJson = taskHttpClient.execute(
							taskStatusRequest, taskResponseHandler);
					JSONObject statusObject = new JSONObject(statusJson);
					JSONObject statusData = statusObject.getJSONObject("data");
					id = statusData.optString("id");
					if (id.equals("")) {
						id = "###";
					}
					type = statusData.getString("type");
					node = statusData.getString("node");
					status = statusData.getString("status");
					user = statusData.getString("user");
					start = new SimpleDateFormat("MMM dd HH:mm:ss")
							.format(new Date(
									statusData.getLong("starttime") * 1000));
					LogStatsActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							taskNameText.setText(id + " (" + type + ")");
							taskNodeText.setText(node);
							taskStatusText.setText(status);
							taskUserText.setText(user);
							taskStartText.setText(start);
						}
					});

				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
						showErrorDialog();
				}
			}
		}).start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater nodesMenu_inflater = getMenuInflater();
		nodesMenu_inflater.inflate(R.menu.log_stats_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.updateLogStatsPref:
			updateTaskStats();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private final ResponseHandler<String> taskResponseHandler = new ResponseHandler<String>() {

		@Override
		public String handleResponse(HttpResponse response)
				throws ClientProtocolException, IOException {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);

			return result;
		}

	};

	private void showErrorDialog() {
		LogStatsActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						LogStatsActivity.this);
				builder.setTitle("Connection error");
				builder.setMessage("Unable to connect. \nDo you want to retry?");
				builder.setCancelable(false);
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								updateTaskStats();
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								dialog.dismiss();
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
