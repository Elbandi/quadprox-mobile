package it.quadrata.android.quad_prox_mob;

import android.androidVNC.ConnectionBean;
import android.androidVNC.VncCanvasActivity;
import android.androidVNC.VncConstants;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.net.URI;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
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
import org.json.JSONObject;

public class VMStatsActivity extends Activity {

	// Authentication credentials
	private static String server;
	private static String ticket;
	private static String token;
	private static String node;
	private static String vmid;
	private static String type;

	// VM info variables
	private static String name;
	private static String status;
	private static int num_cpu;
	private static double cpu_usage_double;
	private static String cpu_usage;
	private static long max_mem;
	private static long mem_usage;
	private static int uptime;
	private static int uptime_d;
	private static int uptime_h;
	private static int uptime_m;
	private static int uptime_s;
	private static String notes;

	// Uptime constants
	private static int MIN = 60;
	private static int HOUR = MIN * 60;
	private static int DAY = HOUR * 24;

	// Migrate variables
	private static ArrayList<String> nodes_list = new ArrayList<String>();
	private static String node_target;
	private static int online_migr;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.vm_stats_layout);

		// VM data retrieving
		Intent vmStatsIntent = VMStatsActivity.this.getIntent();
		server = vmStatsIntent.getStringExtra("server");
		ticket = vmStatsIntent.getStringExtra("ticket");
		token = vmStatsIntent.getStringExtra("token");
		node = vmStatsIntent.getStringExtra("node");
		vmid = vmStatsIntent.getStringExtra("vmid");
		type = vmStatsIntent.getStringExtra("type");

		updateVmStats();
	}

	private void updateVmStats() {
		// VM info views
		final TextView vmNameText = (TextView) findViewById(R.id.vmName);
		final TextView vmStatusText = (TextView) findViewById(R.id.vmStatus);
		final TextView vmIdText = (TextView) findViewById(R.id.vmId);
		final TextView vmCpuText = (TextView) findViewById(R.id.vmCpu);
		final TextView vmMemText = (TextView) findViewById(R.id.vmMem);
		final TextView vmUptimeText = (TextView) findViewById(R.id.vmUptime);
		final TextView vmNotesText = (TextView) findViewById(R.id.vmNotes);

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient updateVmHttpClient = httpApp.getHttpClient();

					// VM stats request
					HttpGet vmStatsRequest = new HttpGet();
					URI vzStatusUri = new URI(server + "/api2/json/nodes/"
							+ node + "/openvz/" + vmid + "/status/current");
					URI qemuStatusUri = new URI(server + "/api2/json/nodes/"
							+ node + "/qemu/" + vmid + "/status/current");
					if (type.equals("vz")) {
						vmStatsRequest.setURI(vzStatusUri);
					} else {
						vmStatsRequest.setURI(qemuStatusUri);
					}
					vmStatsRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					String statsJson = updateVmHttpClient.execute(
							vmStatsRequest, vmStatsResponseHandler).entity_content;
					JSONObject statsObject = new JSONObject(statsJson);
					JSONObject statsData = statsObject.getJSONObject("data");
					name = statsData.getString("name");
					status = statsData.getString("status");
					num_cpu = statsData.getInt("cpus");
					cpu_usage_double = statsData.getDouble("cpu") * 100;
					DecimalFormat cpu_dec_form = new DecimalFormat("#.#");
					cpu_usage = cpu_dec_form.format(cpu_usage_double);
					max_mem = statsData.getLong("maxmem");
					mem_usage = statsData.getLong("mem");
					uptime = statsData.getInt("uptime");
					uptime_d = uptime / DAY;
					uptime_h = (uptime - (uptime_d * DAY)) / HOUR;
					uptime_m = (uptime - ((uptime_d * DAY) + (uptime_h * HOUR)))
							/ MIN;
					uptime_s = uptime
							- ((uptime_d * DAY) + (uptime_h * HOUR) + (uptime_m * MIN));

					// Notes request
					HttpGet notesVmRequest = new HttpGet();
					URI vzNotesUri = new URI(server + "/api2/json/nodes/"
							+ node + "/openvz/" + vmid + "/config");
					URI qemuNotesUri = new URI(server + "/api2/json/nodes/"
							+ node + "/qemu/" + vmid + "/config");
					if (type.equals("vz")) {
						notesVmRequest.setURI(vzNotesUri);
					} else {
						notesVmRequest.setURI(qemuNotesUri);
					}
					notesVmRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					String notesJson = updateVmHttpClient.execute(
							notesVmRequest, vmStatsResponseHandler).entity_content;
					JSONObject notesObject = new JSONObject(notesJson);
					JSONObject notesData = notesObject.getJSONObject("data");
					notes = notesData.optString("description");
					VMStatsActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							vmNameText.setText(name);
							vmStatusText.setText(status);
							vmIdText.setText(vmid);
							vmCpuText.setText(cpu_usage + "% of " + num_cpu
									+ "CPU");
							vmMemText.setText((mem_usage / 1048576) + "MB of "
									+ (max_mem / 1048576) + "MB ");
							vmUptimeText.setText(uptime_d + "d " + uptime_h
									+ "h " + uptime_m + "m " + uptime_s + "s");
							vmNotesText.setText(notes);
						}
					});

					// Migrate nodes list retrieving
					nodes_list.clear();
					HttpGet nodesRequest = new HttpGet(server
							+ "/api2/json/nodes");
					nodesRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String nodesResponse = updateVmHttpClient.execute(
							nodesRequest, vmStatsResponseHandler).entity_content;
					JSONObject nodesObject = new JSONObject(nodesResponse);
					JSONArray nodesArray = nodesObject.getJSONArray("data");
					final int nodesArrayLength = nodesArray.length();
					JSONObject singleNodeObject = new JSONObject();
					for (int i = 0; i <= (nodesArrayLength - 1); i++) {
						singleNodeObject = nodesArray.getJSONObject(i);
						final String nodeSpinnerItem = singleNodeObject
								.getString("node");
						if (!nodeSpinnerItem.equals(node)
								&& !nodes_list.contains(nodeSpinnerItem)) {
							nodes_list.add(nodeSpinnerItem);
						}
					}
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					showRefreshErrorDialog();
				}
			}
		}).start();
	}

	private void startVm() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient startVmHttpClient = httpApp.getHttpClient();
					HttpPost startVmRequest = new HttpPost();
					URI vzStartUri = new URI(server + "/api2/json/nodes/"
							+ node + "/openvz/" + vmid + "/status/start");
					URI qemuStartUri = new URI(server + "/api2/json/nodes/"
							+ node + "/qemu/" + vmid + "/status/start");
					if (type.equals("vz")) {
						startVmRequest.setURI(vzStartUri);
					} else {
						startVmRequest.setURI(qemuStartUri);
					}
					startVmRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					startVmRequest.addHeader("CSRFPreventionToken", token);
					final ResponseObject startResponse = startVmHttpClient
							.execute(startVmRequest, vmStatsResponseHandler);
					String startContent = startResponse.entity_content
							.substring(startResponse.entity_content
									.indexOf("{"));
					JSONObject contentObj = new JSONObject(startContent);
					JSONObject errorsObj = contentObj.optJSONObject("errors");
					if (errorsObj != null) {
						Iterator errorsIterator = errorsObj.keys();
						while (errorsIterator.hasNext()) {
							String error_label = (String) errorsIterator.next();
							startResponse.entity_errors = startResponse.entity_errors
									.concat("\n" + error_label + ": "
											+ errorsObj.getString(error_label));
						}
					}
					if (startResponse.status_code != 200) {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(
										VMStatsActivity.this);
								builder.setCancelable(false);
								builder.setTitle("Http error "
										+ Integer
												.toString(startResponse.status_code));
								builder.setMessage(startResponse.status_reason
										+ startResponse.entity_errors);
								builder.setNeutralButton("Ok",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int id) {
												dialog.dismiss();
											}
										});
								AlertDialog alertDialog = builder.create();
								if (!isFinishing()) {
									alertDialog.show();
								}
							}
						});
					} else {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast startVmToast = Toast.makeText(
										VMStatsActivity.this, name
												+ " start request sent",
										Toast.LENGTH_SHORT);
								startVmToast.show();
							}
						});
					}
					// Updating VM stats
					Thread.sleep(2000);
					updateVmStats();
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					showActionErrorDialog();
				}
			}
		}).start();
	}

	private void stopVm() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient stopVmHttpClient = httpApp.getHttpClient();
					HttpPost stopVmRequest = new HttpPost();
					URI vzStopUri = new URI(server + "/api2/json/nodes/" + node
							+ "/openvz/" + vmid + "/status/stop");
					URI qemuStopUri = new URI(server + "/api2/json/nodes/"
							+ node + "/qemu/" + vmid + "/status/stop");
					if (type.equals("vz")) {
						stopVmRequest.setURI(vzStopUri);
					} else {
						stopVmRequest.setURI(qemuStopUri);
					}
					stopVmRequest
							.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					stopVmRequest.addHeader("CSRFPreventionToken", token);
					final ResponseObject stopResponse = stopVmHttpClient
							.execute(stopVmRequest, vmStatsResponseHandler);
					String stopContent = stopResponse.entity_content
							.substring(stopResponse.entity_content.indexOf("{"));
					JSONObject contentObj = new JSONObject(stopContent);
					JSONObject errorsObj = contentObj.optJSONObject("errors");
					if (errorsObj != null) {
						Iterator errorsIterator = errorsObj.keys();
						while (errorsIterator.hasNext()) {
							String error_label = (String) errorsIterator.next();
							stopResponse.entity_errors = stopResponse.entity_errors
									.concat("\n" + error_label + ": "
											+ errorsObj.getString(error_label));
						}
					}
					if (stopResponse.status_code != 200) {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(
										VMStatsActivity.this);
								builder.setCancelable(false);
								builder.setTitle("Http error "
										+ Integer
												.toString(stopResponse.status_code));
								builder.setMessage(stopResponse.status_reason
										+ stopResponse.entity_errors);
								builder.setNeutralButton("Ok",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int id) {
												dialog.dismiss();
											}
										});
								AlertDialog alertDialog = builder.create();
								if (!isFinishing()) {
									alertDialog.show();
								}
							}
						});
					} else {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast stopVmToast = Toast.makeText(
										VMStatsActivity.this, name
												+ " stop request sent",
										Toast.LENGTH_SHORT);
								stopVmToast.show();
							}
						});
					}
					// Updating VM stats
					Thread.sleep(2000);
					updateVmStats();
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					showActionErrorDialog();
				}
			}
		}).start();
	}

	private void shutdownVm() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient shutdownVmHttpClient = httpApp.getHttpClient();
					HttpPost shutdownVmRequest = new HttpPost();
					URI vzStopUri = new URI(server + "/api2/json/nodes/" + node
							+ "/openvz/" + vmid + "/status/shutdown");
					URI qemuStopUri = new URI(server + "/api2/json/nodes/"
							+ node + "/qemu/" + vmid + "/status/shutdown");
					if (type.equals("vz")) {
						shutdownVmRequest.setURI(vzStopUri);
					} else {
						shutdownVmRequest.setURI(qemuStopUri);
					}
					shutdownVmRequest
							.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					shutdownVmRequest.addHeader("CSRFPreventionToken", token);
					final ResponseObject shutdownResponse = shutdownVmHttpClient
							.execute(shutdownVmRequest, vmStatsResponseHandler);
					String shutdownContent = shutdownResponse.entity_content
							.substring(shutdownResponse.entity_content.indexOf("{"));
					JSONObject contentObj = new JSONObject(shutdownContent);
					JSONObject errorsObj = contentObj.optJSONObject("errors");
					if (errorsObj != null) {
						Iterator errorsIterator = errorsObj.keys();
						while (errorsIterator.hasNext()) {
							String error_label = (String) errorsIterator.next();
							shutdownResponse.entity_errors = shutdownResponse.entity_errors
									.concat("\n" + error_label + ": "
											+ errorsObj.getString(error_label));
						}
					}
					if (shutdownResponse.status_code != 200) {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(
										VMStatsActivity.this);
								builder.setCancelable(false);
								builder.setTitle("Http error "
										+ Integer
												.toString(shutdownResponse.status_code));
								builder.setMessage(shutdownResponse.status_reason
										+ shutdownResponse.entity_errors);
								builder.setNeutralButton("Ok",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int id) {
												dialog.dismiss();
											}
										});
								AlertDialog alertDialog = builder.create();
								if (!isFinishing()) {
									alertDialog.show();
								}
							}
						});
					} else {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast shutdownVmToast = Toast.makeText(
										VMStatsActivity.this, name
												+ " shutdown request sent",
										Toast.LENGTH_SHORT);
								shutdownVmToast.show();
							}
						});
					}
					// Updating VM stats
					Thread.sleep(2000);
					updateVmStats();
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					showActionErrorDialog();
				}
			}
		}).start();
	}

	private void consoleVm() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient consoleVmHttpClient = httpApp.getHttpClient();
					HttpPost consoleVmRequest = new HttpPost();
					URI vzConsoleUri = new URI(server + "/api2/json/nodes/" + node
							+ "/openvz/" + vmid + "/vncproxy"); // nottested
					URI qemuConsoleUri = new URI(server + "/api2/json/nodes/"
							+ node + "/qemu/" + vmid + "/vncproxy");
					if (type.equals("vz")) {
						consoleVmRequest.setURI(vzConsoleUri);
					} else {
						consoleVmRequest.setURI(qemuConsoleUri);
					}
					consoleVmRequest
							.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					consoleVmRequest.addHeader("CSRFPreventionToken", token);
					final ResponseObject consoleResponse = consoleVmHttpClient
							.execute(consoleVmRequest, vmStatsResponseHandler);
					String consoleContent = consoleResponse.entity_content
							.substring(consoleResponse.entity_content.indexOf("{"));
					JSONObject contentObj = new JSONObject(consoleContent);
					JSONObject errorsObj = contentObj.optJSONObject("errors");
					if (errorsObj != null) {
						Iterator errorsIterator = errorsObj.keys();
						while (errorsIterator.hasNext()) {
							String error_label = (String) errorsIterator.next();
							consoleResponse.entity_errors = consoleResponse.entity_errors
									.concat("\n" + error_label + ": "
											+ errorsObj.getString(error_label));
						}
					}
					if (consoleResponse.status_code != 200) {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(
										VMStatsActivity.this);
								builder.setCancelable(false);
								builder.setTitle("Http error "
										+ Integer
												.toString(consoleResponse.status_code));
								builder.setMessage(consoleResponse.status_reason
										+ consoleResponse.entity_errors);
								builder.setNeutralButton("Ok",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int id) {
												dialog.dismiss();
											}
										});
								AlertDialog alertDialog = builder.create();
								if (!isFinishing()) {
									alertDialog.show();
								}
							}
						});
					} else {
						JSONObject consoleData = contentObj.getJSONObject("data");
						ConnectionBean connection = new ConnectionBean();
						connection.setAddress(consoleVmRequest.getURI().getHost());
						connection.setNickname(connection.getAddress());
						connection.setColorModel("C24bit");
						connection.setPort(consoleData.getInt("port"));
						connection.setUserName(consoleData.getString("user"));
						connection.setPassword(consoleData.getString("ticket"));
						connection.setCert(consoleData.getString("cert"));
						Intent intent = new Intent(VMStatsActivity.this, VncCanvasActivity.class);
						intent.putExtra(VncConstants.CONNECTION, connection.Gen_getValues());
//						intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(intent);
					}
					// Updating VM stats
					Thread.sleep(2000);
					updateVmStats();
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					showActionErrorDialog();
				}
			}
		}).start();
	}

	private void migrateVm() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient migrateVmHttpClient = httpApp.getHttpClient();

					HttpPost migrateVmRequest = new HttpPost();
					URI vzStartUri = new URI(server + "/api2/json/nodes/"
							+ node + "/openvz/" + vmid + "/migrate");
					URI qemuStartUri = new URI(server + "/api2/json/nodes/"
							+ node + "/qemu/" + vmid + "/migrate");
					if (type.equals("vz")) {
						migrateVmRequest.setURI(vzStartUri);
					} else {
						migrateVmRequest.setURI(qemuStartUri);
					}
					List<NameValuePair> migrateVmParameters = new ArrayList<NameValuePair>();
					migrateVmParameters.add(new BasicNameValuePair("target",
							node_target));
					if (online_migr == 1) {
						migrateVmParameters.add(new BasicNameValuePair(
								"online", "1"));
					}
					HttpEntity migrateVmEntity = new UrlEncodedFormEntity(
							migrateVmParameters);
					migrateVmRequest.setEntity(migrateVmEntity);
					migrateVmRequest.addHeader("Cookie", "PVEAuthCookie="
							+ ticket);
					migrateVmRequest.addHeader("CSRFPreventionToken", token);
					final ResponseObject migrateResponse = migrateVmHttpClient
							.execute(migrateVmRequest, vmStatsResponseHandler);
					String migrateContent = migrateResponse.entity_content
							.substring(migrateResponse.entity_content
									.indexOf("{"));
					JSONObject contentObj = new JSONObject(migrateContent);
					final String contentData = contentObj.optString("data",
							"null");
					JSONObject errorsObj = contentObj.optJSONObject("errors");
					if (errorsObj != null) {
						Iterator errorsIterator = errorsObj.keys();
						while (errorsIterator.hasNext()) {
							String error_label = (String) errorsIterator.next();
							migrateResponse.entity_errors = migrateResponse.entity_errors
									.concat("\n" + error_label + ": "
											+ errorsObj.getString(error_label));
						}
					}
					if (migrateResponse.status_code != 200) {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								AlertDialog.Builder builder = new AlertDialog.Builder(
										VMStatsActivity.this);
								builder.setCancelable(false);
								builder.setTitle("Http error "
										+ Integer
												.toString(migrateResponse.status_code));
								builder.setMessage(migrateResponse.status_reason
										+ migrateResponse.entity_errors);
								builder.setNeutralButton("Ok",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int id) {
												dialog.dismiss();
											}
										});
								AlertDialog alertDialog = builder.create();
								if (!isFinishing()) {
									alertDialog.show();
								}
							}
						});
					} else {
						VMStatsActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								Toast migrateVmToast = Toast.makeText(
										VMStatsActivity.this, name
												+ " migrate request sent",
										Toast.LENGTH_SHORT);
								migrateVmToast.show();
								AlertDialog.Builder builder = new AlertDialog.Builder(
										VMStatsActivity.this);
								builder.setCancelable(false);
								builder.setMessage("Do you want to check progress?");
								builder.setPositiveButton("Yes",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int id) {
												VMStatsActivity.this.finish();
												Intent logIntent = new Intent(
														VMStatsActivity.this,
														LogStatsActivity.class);
												logIntent.putExtra("server",
														server);
												logIntent.putExtra("ticket",
														ticket);
												logIntent
														.putExtra("node", node);
												logIntent.putExtra("upid",
														contentData);
												startActivity(logIntent);
											}
										});
								builder.setNegativeButton("No",
										new DialogInterface.OnClickListener() {
											@Override
											public void onClick(
													DialogInterface dialog,
													int id) {
												VMStatsActivity.this.finish();
											}
										});
								AlertDialog alertDialog = builder.create();
								if (!isFinishing()) {
									alertDialog.show();
								}
							}
						});
					}
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "null");
					}
					showActionErrorDialog();
				}
			}
		}).start();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater nodesMenu_inflater = getMenuInflater();
		nodesMenu_inflater.inflate(R.menu.vm_stats_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.updateVmPref:
			updateVmStats();
			return true;
		case R.id.startVmPref:
			VMStatsActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					AlertDialog.Builder builder = new AlertDialog.Builder(
							VMStatsActivity.this);
					builder.setCancelable(false);
					builder.setMessage("Confirm starting of " + name + "?");
					builder.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									startVm();
								}
							});
					builder.setNegativeButton("No",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.dismiss();
								}
							});
					AlertDialog alertDialog = builder.create();
					alertDialog.show();
				}
			});
			return true;
		case R.id.stopVmPref:
			VMStatsActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					AlertDialog.Builder builder = new AlertDialog.Builder(
							VMStatsActivity.this);
					builder.setCancelable(false);
					builder.setMessage("Do you really want to stop " + name
							+ "?");
					builder.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									stopVm();
								}
							});
					builder.setNegativeButton("No",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.dismiss();
								}
							});
					AlertDialog alertDialog = builder.create();
					alertDialog.show();
				}
			});
			return true;
        case R.id.shutdownVmPref:
			VMStatsActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					AlertDialog.Builder builder = new AlertDialog.Builder(
							VMStatsActivity.this);
					builder.setCancelable(false);
					builder.setMessage("Do you really want to shutdown " + name
							+ "?");
					builder.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									shutdownVm();
								}
							});
					builder.setNegativeButton("No",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.dismiss();
								}
							});
					AlertDialog alertDialog = builder.create();
					alertDialog.show();
				}
			});
			return true;
        case R.id.consoleVmPref:
			VMStatsActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					AlertDialog.Builder builder = new AlertDialog.Builder(
							VMStatsActivity.this);
					builder.setCancelable(false);
					builder.setMessage("Do you really want to view the console for  " + name
							+ "?");
					builder.setPositiveButton("Yes",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									consoleVm();
								}
							});
					builder.setNegativeButton("No",
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int id) {
									dialog.dismiss();
								}
							});
					AlertDialog alertDialog = builder.create();
					alertDialog.show();
				}
			});
			return true;
		case R.id.migrateVmPref:
			VMStatsActivity.this.runOnUiThread(new Runnable() {
				@Override
				public void run() {
					final ArrayAdapter<String> nodesAdapter = new ArrayAdapter<String>(
							VMStatsActivity.this,
							android.R.layout.simple_spinner_item, nodes_list);
					nodesAdapter
							.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

					if (nodesAdapter.isEmpty()) {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								VMStatsActivity.this);
						builder.setCancelable(false);
						builder.setMessage("No target nodes available");
						builder.setNeutralButton("Ok",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.dismiss();
									}
								});
						AlertDialog alertDialog = builder.create();
						alertDialog.show();
					} else {
						AlertDialog.Builder builder = new AlertDialog.Builder(
								VMStatsActivity.this);
						LayoutInflater migrateInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
						View migrateDialogLayout = migrateInflater.inflate(
								R.layout.migrate_dialog_layout, null);
						Spinner nodeSpinner = (Spinner) migrateDialogLayout
								.findViewById(R.id.migrateNode_spinner);
						nodeSpinner.setAdapter(nodesAdapter);

						nodeSpinner
								.setOnItemSelectedListener(new OnItemSelectedListener() {
									@Override
									public void onItemSelected(
											AdapterView<?> parent, View view,
											int pos, long id) {
										node_target = (String) parent
												.getSelectedItem();
									}

									@Override
									public void onNothingSelected(
											AdapterView<?> parent) {
									}
								});

						online_migr = 0;
						CheckBox onlineMigrateCheck = (CheckBox) migrateDialogLayout
								.findViewById(R.id.onlineMigrate_check);
						if (online_migr == 0) {
							onlineMigrateCheck.setChecked(false);
						} else {
							onlineMigrateCheck.setChecked(true);
						}
						onlineMigrateCheck
								.setOnCheckedChangeListener(new OnCheckedChangeListener() {
									@Override
									public void onCheckedChanged(
											CompoundButton buttonView,
											boolean isChecked) {
										if (isChecked == true) {
											online_migr = 1;
										} else {
											online_migr = 0;
										}
									}
								});

						builder.setPositiveButton("Ok",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int id) {
										migrateVm();
									}
								});

						builder.setNegativeButton("Cancel",
								new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialog,
											int id) {
										dialog.dismiss();
									}
								});

						builder.setTitle("Migrate to");
						builder.setCancelable(false);
						builder.setView(migrateDialogLayout);
						AlertDialog migrateDialog = builder.create();
						migrateDialog.show();
					}
				}
			});
			return true;
		case R.id.logPref:
			Intent logIntent = new Intent(VMStatsActivity.this,
					ClusterLogActivity.class);
			logIntent.putExtra("server", server);
			logIntent.putExtra("ticket", ticket);
			logIntent.putExtra("logHost", vmid);
			startActivity(logIntent);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private static class ResponseObject {
		public String entity_content;
		public String entity_errors = "";
		public int status_code;
		public String status_reason;
	}

	private final ResponseHandler<ResponseObject> vmStatsResponseHandler = new ResponseHandler<ResponseObject>() {

		@Override
		public ResponseObject handleResponse(HttpResponse response)
				throws ClientProtocolException, IOException {

			ResponseObject object = new ResponseObject();
			object.entity_content = EntityUtils.toString(response.getEntity());
			object.status_code = response.getStatusLine().getStatusCode();
			object.status_reason = response.getStatusLine().getReasonPhrase();

			return object;
		}

	};

	private void showRefreshErrorDialog() {
		VMStatsActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						VMStatsActivity.this);
				builder.setTitle("Connection error");
				builder.setMessage("Unable to connect. \nDo you want to retry?");
				builder.setCancelable(false);
				builder.setPositiveButton("Ok",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								updateVmStats();
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								VMStatsActivity.this.finish();
							}
						});
				AlertDialog alertDialog = builder.create();
				if (!isFinishing()) {
					alertDialog.show();
				}
			}
		});
	}

	private void showActionErrorDialog() {
		VMStatsActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						VMStatsActivity.this);
				builder.setTitle("Connection error");
				builder.setMessage("Unable to connect. \nRetry later.");
				builder.setCancelable(false);
				builder.setNeutralButton("Ok",
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