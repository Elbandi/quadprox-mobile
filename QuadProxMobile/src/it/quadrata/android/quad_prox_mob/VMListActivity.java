package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;

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
import android.graphics.drawable.Drawable;
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
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class VMListActivity extends Activity {

	// Authentication credentials
	private static String server;
	private static String ticket;
	private static String token;

	// Node info variables
	private static String node;
	private static String node_vers;
	private static int node_index;
	private static double node_cpu_usage_double;
	private static String node_cpu_usage;
	private static int node_max_cpu;
	private static double node_mem_double;
	private static double node_max_mem_double;
	private static String node_mem;
	private static String node_max_mem;
	private static int node_uptime;
	private static int node_uptime_d;
	private static int node_uptime_h;
	private static int node_uptime_m;
	private static int node_uptime_s;

	// Uptime constants
	private static int MIN = 60;
	private static int HOUR = MIN * 60;
	private static int DAY = HOUR * 24;

	// VM info variables
	final DecimalFormat cpu_dec_form = new DecimalFormat("#.#");
	private static double vm_cpu;
	private static long vm_mem;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.vm_list_layout);

		// Node data retrieving
		Intent vmListIntent = VMListActivity.this.getIntent();
		server = vmListIntent.getStringExtra("server");
		ticket = vmListIntent.getStringExtra("ticket");
		token = vmListIntent.getStringExtra("token");
		node = vmListIntent.getStringExtra("node");
		node_vers = vmListIntent.getStringExtra("node_vers");
		node_index = vmListIntent.getIntExtra("node_index", 0);

		buildVMList();
	}

	private void buildVMList() {
		// Node info views
		final TextView nodeInfo = (TextView) findViewById(R.id.nodeInfo);
		final TextView nodeVers = (TextView) findViewById(R.id.nodeVers);
		final TextView nodeVZ = (TextView) findViewById(R.id.nodeVZ);
		final TextView nodeQM = (TextView) findViewById(R.id.nodeQM);
		final TextView nodeCpu = (TextView) findViewById(R.id.nodeCpu);
		final TextView nodeMem = (TextView) findViewById(R.id.nodeMem);
		final TextView nodeUptime = (TextView) findViewById(R.id.nodeUptime);

		// VMs list view and array
		ListView vmListView = (ListView) findViewById(R.id.vmList);
		final ArrayAdapter<VmItem> vmArrayAdapter = new ArrayAdapter<VmItem>(
				this, R.layout.vm_row_layout, R.id.vmRow) {
			@Override
			public View getView(int position, View convertView, ViewGroup parent) {
				return getVmRowHolder(position, convertView, parent);
			}

			public View getVmRowHolder(int position, View convertView,
					ViewGroup parent) {
				VmRowHolder rowHolder = null;
				if (convertView == null) {
					LayoutInflater nodeInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
					convertView = nodeInflater.inflate(R.layout.vm_row_layout,
							null);
					rowHolder = new VmRowHolder();
					rowHolder.vmRowName = (TextView) convertView
							.findViewById(R.id.vmRowName);
					rowHolder.vmRowCpu = (TextView) convertView
							.findViewById(R.id.vmRowCpu);
					rowHolder.vmRowMem = (TextView) convertView
							.findViewById(R.id.vmRowMem);
					rowHolder.vmRowId = (TextView) convertView
							.findViewById(R.id.vmRowId);
					rowHolder.vmIcon = (ImageView) convertView
							.findViewById(R.id.vmIcon);
					rowHolder.vmTypeIcon = (ImageView) convertView
							.findViewById(R.id.vmTypeIcon);
					convertView.setTag(rowHolder);
				} else {
					rowHolder = (VmRowHolder) convertView.getTag();
				}
				VmItem item = getItem(position);
				rowHolder.vmRowName.setText(item.vm_name);
				rowHolder.vmRowCpu.setText(item.vm_cpu);
				rowHolder.vmRowMem.setText(item.vm_mem);
				rowHolder.vmRowId.setText(item.vm_id);
				rowHolder.vmIcon.setImageDrawable(item.vm_icon);
				rowHolder.vmTypeIcon.setImageDrawable(item.vm_type_icon);
				return convertView;
			}
		};
		final ArrayList<String> vmIdArray = new ArrayList<String>();
		final ArrayList<String> vmTypeArray = new ArrayList<String>();

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					if (isOnline() == false) {
						throw new Exception();
					}
					ProxmoxCustomApp httpApp = (ProxmoxCustomApp) getApplication();
					HttpClient serverHttpClient = httpApp.getHttpClient();

					// Node info request
					HttpGet nodesRequest = new HttpGet(server
							+ "/api2/json/nodes");
					nodesRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String nodesResponse = serverHttpClient.execute(
							nodesRequest, serverResponseHandler);
					JSONObject nodesObject = new JSONObject(nodesResponse);
					JSONArray nodesJsonArray = nodesObject.getJSONArray("data");
					JSONObject nodeObject = nodesJsonArray
							.getJSONObject(node_index);
					node = nodeObject.getString("node");
					node_cpu_usage_double = nodeObject.optDouble("cpu", 0) * 100;
					node_cpu_usage = cpu_dec_form.format(node_cpu_usage_double);
					node_max_cpu = nodeObject.optInt("maxcpu", 0);
					node_mem_double = nodeObject.optDouble("mem", 0);
					node_max_mem_double = nodeObject.optDouble("maxmem", 0);
					node_mem = cpu_dec_form
							.format(node_mem_double / 1073741824);
					node_max_mem = cpu_dec_form
							.format(node_max_mem_double / 1073741824);
					node_uptime = nodeObject.optInt("uptime", 0);
					node_uptime_d = node_uptime / DAY;
					node_uptime_h = (node_uptime - (node_uptime_d * DAY))
							/ HOUR;
					node_uptime_m = (node_uptime - ((node_uptime_d * DAY) + (node_uptime_h * HOUR)))
							/ MIN;
					node_uptime_s = node_uptime
							- ((node_uptime_d * DAY) + (node_uptime_h * HOUR) + (node_uptime_m * MIN));
					VMListActivity.this.runOnUiThread(new Runnable() {
						@Override
						public void run() {
							nodeInfo.setText(node);
							nodeVers.setText("Proxmox VE " + node_vers);
							nodeCpu.setText(node_cpu_usage + "% ("
									+ node_max_cpu + " cpu)");
							nodeMem.setText(node_mem + "GB of " + node_max_mem
									+ "GB");
							nodeUptime.setText(node_uptime_d + "d "
									+ node_uptime_h + "h " + node_uptime_m
									+ "m " + node_uptime_s + "s");
						}
					});

					// VZs list items creation
					HttpGet vzRequest = new HttpGet(server
							+ "/api2/json/nodes/" + node + "/openvz");
					vzRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String vzResponse = serverHttpClient.execute(vzRequest,
							serverResponseHandler);
					JSONObject vzObject = new JSONObject(vzResponse);
					JSONArray vzJsonArray = vzObject.getJSONArray("data");
					final int vzJsonArrayLength = vzJsonArray.length();
					nodeVZ.post(new Runnable() {
						@Override
						public void run() {
							nodeVZ.setText(Integer.toString(vzJsonArrayLength));
						}
					});
					JSONObject singleVzObject = new JSONObject();
					for (int i = 0; i <= (vzJsonArrayLength - 1); i++) {
						singleVzObject = vzJsonArray.getJSONObject(i);
						final VmItem item = new VmItem();
						item.vm_name = singleVzObject.getString("name");
						vm_cpu = singleVzObject.getDouble("cpu");
						item.vm_cpu = cpu_dec_form.format(vm_cpu) + "%";
						vm_mem = singleVzObject.getLong("mem");
						item.vm_mem = Long.toString(vm_mem / 1048576) + "MB";
						item.vm_type_icon = getResources().getDrawable(
								R.drawable.ic_vm_type_vz);
						item.vm_id = singleVzObject.getString("vmid");
						vmIdArray.add(item.vm_id);
						vmTypeArray.add("vz");
						String status = singleVzObject.getString("status");
						if (status.equals("running")) {
							item.vm_icon = getResources().getDrawable(
									R.drawable.ic_vm_list_on);
						} else {
							item.vm_icon = getResources().getDrawable(
									R.drawable.ic_vm_list_off);
						}
						VMListActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								vmArrayAdapter.add(item);
							}
						});
					}

					// QMs list items creation
					HttpGet qemuRequest = new HttpGet(server
							+ "/api2/json/nodes/" + node + "/qemu");
					qemuRequest.addHeader("Cookie", "PVEAuthCookie=" + ticket);
					String qemuResponse = serverHttpClient.execute(qemuRequest,
							serverResponseHandler);
					JSONObject qemuObject = new JSONObject(qemuResponse);
					JSONArray qemuJsonArray = qemuObject.getJSONArray("data");
					final int qemuJsonArrayLength = qemuJsonArray.length();
					nodeQM.post(new Runnable() {
						@Override
						public void run() {
							nodeQM.setText(Integer
									.toString(qemuJsonArrayLength));
						}
					});
					JSONObject singleQemuObject = new JSONObject();
					for (int i = 0; i <= (qemuJsonArrayLength - 1); i++) {
						singleQemuObject = qemuJsonArray.getJSONObject(i);
						final VmItem item = new VmItem();
						item.vm_name = singleQemuObject.getString("name");
						vm_cpu = singleQemuObject.getDouble("cpu");
						item.vm_cpu = cpu_dec_form.format(vm_cpu) + "%";
						vm_mem = singleQemuObject.getLong("mem");
						item.vm_mem = Long.toString(vm_mem / 1048576) + "MB";
						item.vm_type_icon = getResources().getDrawable(
								R.drawable.ic_vm_type_qm);
						item.vm_id = singleQemuObject.getString("vmid");
						vmIdArray.add(item.vm_id);
						vmTypeArray.add("vm");
						String status = singleQemuObject.getString("status");
						if (status.equals("running")) {
							item.vm_icon = getResources().getDrawable(
									R.drawable.ic_vm_list_on);
						} else {
							item.vm_icon = getResources().getDrawable(
									R.drawable.ic_vm_list_off);
						}
						VMListActivity.this.runOnUiThread(new Runnable() {
							@Override
							public void run() {
								vmArrayAdapter.add(item);
							}
						});
					}
				} catch (Exception e) {
					if (e.getMessage() != null) {
						Log.e(e.getClass().getName(), e.getMessage());
					} else {
						Log.e(e.getClass().getName(), "No error message");
					}
					if (isOnline() == false) {
						showErrorDialog();
					}
				}
			}
		}).start();
		vmListView.setAdapter(vmArrayAdapter);

		vmListView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				int pos = parent.getPositionForView(view);
				String vmid = vmIdArray.get(pos);
				String type = vmTypeArray.get(pos);
				Intent vmStatsIntent = new Intent(VMListActivity.this,
						VMStatsActivity.class);
				// Putting VM data into the intent for VM stats activity
				vmStatsIntent.putExtra("server", server);
				vmStatsIntent.putExtra("ticket", ticket);
				vmStatsIntent.putExtra("token", token);
				vmStatsIntent.putExtra("node", node);
				vmStatsIntent.putExtra("vmid", vmid);
				vmStatsIntent.putExtra("type", type);
				startActivity(vmStatsIntent);
			}
		});

	}

	private static class VmItem {
		// Node info
		public String vm_name;
		public String vm_cpu;
		public String vm_mem;
		public String vm_id;
		public Drawable vm_icon;
		public Drawable vm_type_icon;
	}

	private static class VmRowHolder {
		public TextView vmRowName;
		public TextView vmRowCpu;
		public TextView vmRowMem;
		public TextView vmRowId;
		public ImageView vmIcon;
		public ImageView vmTypeIcon;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater nodesMenu_inflater = getMenuInflater();
		nodesMenu_inflater.inflate(R.menu.vm_list_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.updateVmPref:
			buildVMList();
			return true;
		case R.id.logPref:
			Intent logIntent = new Intent(VMListActivity.this,
					ClusterLogActivity.class);
			logIntent.putExtra("server", server);
			logIntent.putExtra("ticket", ticket);
			logIntent.putExtra("logHost", node);
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

	private void showErrorDialog() {
		VMListActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				AlertDialog.Builder builder = new AlertDialog.Builder(
						VMListActivity.this);
				builder.setTitle("Connection error");
				builder.setMessage("Unable to connect. \nDo you want to retry?");
				builder.setCancelable(false);
				builder.setPositiveButton("Yes",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								buildVMList();
							}
						});
				builder.setNegativeButton("No",
						new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialog, int id) {
								VMListActivity.this.finish();
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