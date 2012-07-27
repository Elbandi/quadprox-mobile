package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
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
							vmStatsRequest, vmStatsResponseHandler);
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
							notesVmRequest, vmStatsResponseHandler);
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
							nodesRequest, vmStatsResponseHandler);
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
					String startResponse = startVmHttpClient.execute(
							startVmRequest, vmStatsResponseHandler);
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
					String stopResponse = stopVmHttpClient.execute(
							stopVmRequest, vmStatsResponseHandler);
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
					HttpResponse migrateResponse = migrateVmHttpClient
							.execute(migrateVmRequest);
					HttpEntity migrateEntity = migrateResponse.getEntity();
					String migrateContent = EntityUtils.toString(migrateEntity);
					String migrateStatusCode = Integer.toString(migrateResponse
							.getStatusLine().getStatusCode());
					String migrateStatusReason = migrateResponse
							.getStatusLine().getReasonPhrase();
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
							builder.setMessage("Do you want to check logs?");
							builder.setPositiveButton("Yes",
									new DialogInterface.OnClickListener() {
										@Override
										public void onClick(
												DialogInterface dialog, int id) {
											VMStatsActivity.this.finish();
											Intent logIntent = new Intent(
													VMStatsActivity.this,
													ClusterLogActivity.class);
											logIntent
													.putExtra("server", server);
											logIntent
													.putExtra("ticket", ticket);
											startActivity(logIntent);
										}
									});
							builder.setNegativeButton("No",
									new DialogInterface.OnClickListener() {
										@Override
										public void onClick(
												DialogInterface dialog, int id) {
											VMStatsActivity.this.finish();
										}
									});
							AlertDialog alertDialog = builder.create();
							alertDialog.show();
						}
					});
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
			startActivity(logIntent);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	private final ResponseHandler<String> vmStatsResponseHandler = new ResponseHandler<String>() {

		@Override
		public String handleResponse(HttpResponse response)
				throws ClientProtocolException, IOException {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);

			return result;
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
				alertDialog.show();
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
				alertDialog.show();
			}
		});
	}

	public boolean isOnline() {
		ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
		return (networkInfo != null && networkInfo.isConnected());
	}
}