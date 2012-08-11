package it.quadrata.android.quad_prox_mob;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

public class WidgetConfigActivity extends Activity {
	private static final String TAG = "it.quadrata.android.quad_prox_mob.WidgetConfigActivity";
	private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
	private Button savePrefsButton;
	private EditText fontSizeInput;
	private EditText intervalInput;
	
	@Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.widget_config_layout);
        
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
        	widgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
     
        // If they gave us an intent without the widget id, just bail.
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
        }
            
		fontSizeInput = (EditText) findViewById(R.id.fontSize);
		intervalInput = (EditText) findViewById(R.id.interval);
		SharedPreferences widgetPrefs = 
				getSharedPreferences("WidgetPrefs_" + widgetId, Context.MODE_PRIVATE);
		
		try {
			int fontSize = 
				Integer.valueOf(widgetPrefs.getString("fontSize", WidgetPrefsActivity.defaultFontSize));
			int interval = 
				Integer.valueOf(widgetPrefs.getString("interval", WidgetPrefsActivity.defaultUpdateInterval));
			fontSizeInput.setText(String.valueOf(fontSize));
			intervalInput.setText(String.valueOf(interval));
		} catch (Exception e) {
			if (e.getMessage() == null) {
				Log.i(TAG, "Null pointer Exception");
			}
			else {
				Log.i(TAG, e.getMessage());
			}
		}
		
        // Retrieving of login preferences
		Context context = WidgetConfigActivity.this;
		String cluster = getClusterName(context);
		if (cluster == null || cluster.length() == 0) {
			cluster = "No Name";
		}
		String[] items = new String[] {cluster};
		Spinner spinner = (Spinner) findViewById(R.id.cluster);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);

		savePrefsButton = (Button) findViewById(R.id.savePrefsButton);
        savePrefsButton.setOnClickListener(savePrefsButtonOnClickListener);
    }
	
	protected Intent get_ACTION_APPWIDGET_UPDATE_Intent(Context context) {
		ComponentName thisAppWidget = new ComponentName(context.getPackageName(),
				WidgetProvider.class.getName());
		int[] appWidgetIds = AppWidgetManager.getInstance(context).getAppWidgetIds(thisAppWidget);
		Intent intent = new Intent(context, WidgetProvider.class);
		intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
		intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
		return intent;
	}
    
	public static String getClusterName(final Context context) {
		final AtomicReference<String> clusterName = new AtomicReference<String>();
		
		Thread t = new Thread(new Runnable() {
			SharedPreferences authPref = context.getSharedPreferences("AuthPref",
					Context.MODE_PRIVATE);
			final String server = authPref.getString("server", "");
			final String username = authPref.getString("username", "");
			final String realm = authPref.getString("realm", "");
			final String password = authPref.getString("password", "");
			
			@Override
			public void run() {
				try {
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
					String ticket = data.getString("ticket");
					//String token = data.getString("CSRFPreventionToken");

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
					clusterName.set(clusterInfo.optString("name"));
				} catch (JSONException e) {
					if (e.getMessage() != null) {
						Log.e(TAG, e.getMessage());
					} else {
						Log.e(TAG, "null");
					}
				} catch (IOException e) {
					if (e.getMessage() != null) {
						Log.e(TAG, e.getMessage());
					} else {
						Log.e(TAG, "null");
					}
				} catch (RuntimeException e) {
					if (e.getMessage() != null) {
						Log.e(TAG, e.getMessage());
					} else {
						Log.e(TAG, "null");
					}
				}
			}
		});
		try {
			t.start();
			t.join();
		} catch (InterruptedException e) {
			if (e.getMessage() != null) {
				Log.e(TAG, e.getMessage());
			} else {
				Log.e(TAG, "null");
			}
		}
		
		return clusterName.get();
	}
	
    private static ResponseHandler<String> serverResponseHandler = new ResponseHandler<String>() {

		@Override
		public String handleResponse(HttpResponse response)
				throws ClientProtocolException, IOException {
			HttpEntity entity = response.getEntity();
			String result = EntityUtils.toString(entity);

			return result;
		}

	};

	private Button.OnClickListener savePrefsButtonOnClickListener =
			new Button.OnClickListener(){
		private boolean error = true;
		
		@Override
		public void onClick(View v) {
			int duration = 0;
			Editable fontSize = fontSizeInput.getText();
			Editable interval = intervalInput.getText();
			SharedPreferences widgetPrefs = 
					getSharedPreferences("WidgetPrefs_" + widgetId, Context.MODE_PRIVATE);
			SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();	
			
			try {
				int fontsize = Integer.parseInt(fontSize.toString());
				if (fontsize > 0) {
					error = false;
					widgetPrefsEditor.putString("fontSize", Integer.toString(fontsize));
				}
				else {
					fontSize.clear();
					fontSizeInput.setError("Error: Number < 1");
				}
			} catch (NumberFormatException nfe) {
				fontSize.clear();
				fontSizeInput.setError("Error: Not number");
			}
			try {
				duration = Integer.parseInt(interval.toString());
				if (duration > 0) {
					error = false;
					widgetPrefsEditor.putString("interval", Integer.toString(duration));
				}
				else {
					interval.clear();
					intervalInput.setError("Error: Number < 1");
				}
			} catch (NumberFormatException nfe) {
				interval.clear();
				intervalInput.setError("Error: Not number");
			}
			if (error == false) {
				widgetPrefsEditor.commit();

				final Context context = WidgetConfigActivity.this;
				AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);

				Uri uriData = Uri.withAppendedPath(
						Uri.parse(WidgetProvider.URI_SCHEME + "://widget/id/"), String.valueOf(widgetId));

				// First update
				ComponentName thisAppWidget = new ComponentName(context.getPackageName(),
						WidgetConfigActivity.class.getName());
				Intent firstUpdate = new Intent(WidgetConfigActivity.this, WidgetProvider.class);
				int[] appWidgetIds = appWidgetManager.getAppWidgetIds(thisAppWidget);
				firstUpdate.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE/*"android.appwidget.action.APPWIDGET_UPDATE"*/);
				firstUpdate.setData(uriData);
				firstUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds);
				//Put the ID of our widget to identify it later.
				firstUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				context.sendBroadcast(firstUpdate);
				
				Intent intentUpdate = new Intent(context, WidgetProvider.class);
				intentUpdate.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
				intentUpdate.setData(uriData);//One Alarm per instance.
				//We will need the exact instance to identify the intent.
				intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				WidgetProvider.addUri(widgetId, uriData);
				PendingIntent pendingIntentAlarm = PendingIntent.getBroadcast(
						WidgetConfigActivity.this, 0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT);
				AlarmManager alarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);
				alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() +
						(duration * 60 * 1000), (duration * 60 * 1000), pendingIntentAlarm);
				Log.d(TAG, "Created Alarm. Action URI = " + uriData.toString() +
						" Minuts = " + duration);
			      
				//Return the original widget ID, found in onCreate().
				Intent resultValue = new Intent();
				resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				setResult(RESULT_OK, resultValue);
				finish();
			}
		}
	};
	
}
