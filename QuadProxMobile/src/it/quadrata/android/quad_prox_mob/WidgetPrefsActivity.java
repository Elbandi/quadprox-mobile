package it.quadrata.android.quad_prox_mob;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.TabActivity;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TabHost.TabSpec;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("deprecation")
public class WidgetPrefsActivity extends TabActivity {
	public final static String defaultFontSize = "9";
	public final static String defaultUpdateInterval = "30";
	private int widgetId;
	private static final String TAG = "it.quadrata.android.quad_prox_mob.WidgetPrefsActivity";
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.widget_prefs_layout);
		
		Intent intent = getIntent();
		widgetId = intent.getIntExtra(
				AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
		if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID)
			finish();
		
		TabHost tabHost = getTabHost();

		// Tab for Preferences
		TabSpec prefsSpec = tabHost.newTabSpec("Preferences");
		// setting Title and Icon for the Tab
		prefsSpec.setIndicator("Preferences", getResources().getDrawable(R.drawable.icon_prefs_tab));
		Intent prefsIntent = new Intent(this, PrefsActivity.class);
		prefsIntent.putExtra("widgetId", widgetId);
		prefsSpec.setContent(prefsIntent);

		// Tab for Notifications
		TabSpec notifySpec = tabHost.newTabSpec("Notifications");
		notifySpec.setIndicator("Notifications", getResources().getDrawable(R.drawable.icon_notify_tab));
		Intent notifyIntent = new Intent(this, NotifyActivity.class);
		notifyIntent.putExtra("widgetId", widgetId);
		notifySpec.setContent(notifyIntent);

		// Tab for Accounts
		TabSpec accountSpec = tabHost.newTabSpec("Accounts");
		accountSpec.setIndicator("Accounts", getResources().getDrawable(R.drawable.icon_accounts_tab));
		Intent accountIntent = new Intent(this, AccountsActivity.class);
		accountIntent.putExtra("widgetId", widgetId);
		accountSpec.setContent(accountIntent);

		// Adding all TabSpec to TabHost
		tabHost.addTab(prefsSpec); // Adding photos tab
		tabHost.addTab(notifySpec); // Adding songs tab
		tabHost.addTab(accountSpec); // Adding videos tab
	}
	
	public void prefsCancelClicked(View v) {
		finish();
	}

	public void prefsSaveClicked(View v) {
		Map<String, String> newPrefs = new HashMap<String, String>();
		int duration = 0;
		Context context = v.getContext();
		
		Map<String, Editable> textPrefs = TextChangeListener.getPrefs();
		for (Map.Entry<String, Editable> entry : textPrefs.entrySet()) {
			String value = entry.getValue().toString();
			String key = entry.getKey();
			Log.i(TAG, "Updating preferences [id:" + widgetId + ":"
					+ key + ":" + value + "]");
			if (key.equals("interval"))
				duration = Integer.valueOf(value);
			newPrefs.put(key, value);
		}
		Map<String, Boolean> checkboxPrefs = CheckboxChangeListener.getPrefs();
		for (Map.Entry<String, Boolean> entry : checkboxPrefs.entrySet()) {
			String value;
			if (entry.getValue())
				value = "true"; 				
			else
				value = "false";
			String key = entry.getKey();
			Log.i(TAG, "Updating preferences [id:" + widgetId + ":"
					+ key + ":" + value + "]");
			newPrefs.put(key, value);
		}
		
		if (! newPrefs.isEmpty()) {
			setPrefs(WidgetPrefsActivity.this, widgetId, newPrefs);

			if (duration > 0) {
				Uri uriData = Uri.withAppendedPath(
						Uri.parse(WidgetProvider.URI_SCHEME + "://widget/id/"), String.valueOf(widgetId));

				Intent intentUpdate = new Intent(context, WidgetProvider.class);
				intentUpdate.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
				intentUpdate.setData(uriData);//One Alarm per instance.
				//We will need the exact instance to identify the intent.
				intentUpdate.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
				WidgetProvider.addUri(widgetId, uriData);
				PendingIntent pendingIntentAlarm = PendingIntent.getBroadcast(
						WidgetPrefsActivity.this, 0, intentUpdate, PendingIntent.FLAG_UPDATE_CURRENT);
				AlarmManager alarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);
				alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() +
						(duration * 60 * 1000), (duration * 60 * 1000), pendingIntentAlarm);
				Log.d(TAG, "Created Alarm. Action URI = " + uriData.toString() +
						" Minuts = " + duration);
			}

            Intent refreshIntent = WidgetProvider.get_ACTION_APPWIDGET_UPDATE_Intent(context, widgetId);
            Log.i(TAG, "Broadcast refresh Intent");
            context.sendBroadcast(refreshIntent);
		}
		
		finish();
	}

	public static synchronized void setPrefs(Context context, int widgetId,
			Map<String, String> prefs) {
		
		SharedPreferences widgetPrefs = 
				context.getSharedPreferences("WidgetPrefs_" + widgetId, Context.MODE_PRIVATE);
		SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();
		
		for (Map.Entry<String, String> entry : prefs.entrySet()) {
			widgetPrefsEditor.putString(entry.getKey(), entry.getValue());
		}
		//widgetPrefsEditor.clear();
		
		widgetPrefsEditor.commit();
	}

    public static synchronized void setStatus(Context context, int widgetId, 
    		String node, String status) {
		SharedPreferences widgetPrefs = 
				context.getSharedPreferences("WidgetPrefs_" + widgetId, Context.MODE_PRIVATE);
		SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();
		widgetPrefsEditor.putString("cpuStatus:" + node, status);
		widgetPrefsEditor.commit();
    }
    
    public static String getStatus(Context context, int widgetId, String node) {
		SharedPreferences widgetPrefs = 
				context.getSharedPreferences("WidgetPrefs_" + widgetId, Context.MODE_PRIVATE);
		return widgetPrefs.getString("cpuStatus:" + node, null);
    }
    
	public static Map<String, String> getPrefs(Context context, int widgetId) {
		Map<String, String> prefs = new HashMap<String, String>();
		
		SharedPreferences widgetPrefs = 
				context.getSharedPreferences("WidgetPrefs_" + widgetId, Context.MODE_PRIVATE);

		prefs.put("notifyEnable", widgetPrefs.getString("notifyEnable", "false"));
		prefs.put("notifyChange", widgetPrefs.getString("notifyChange", "false"));
		prefs.put("notifyCpu", widgetPrefs.getString("notifyCpu", "false"));
		prefs.put("cpuLimit", widgetPrefs.getString("cpuLimit", null));
		prefs.put("notifyMem", widgetPrefs.getString("notifyMem", "false"));
		prefs.put("memLimit", widgetPrefs.getString("memLimit", null));
		prefs.put("fontSize", widgetPrefs.getString("fontSize", defaultFontSize));
		prefs.put("interval", widgetPrefs.getString("interval", defaultUpdateInterval));
		prefs.put("cluster", widgetPrefs.getString("cluster", null));
		
		return prefs;
	}
    
    public static synchronized long getNextId(Context context, int widgetId) {
        long id;
        
		SharedPreferences widgetPrefs = 
				context.getSharedPreferences("WidgetPrefs_" + widgetId, Context.MODE_PRIVATE);
        id = widgetPrefs.getLong("rowId", 0);
        SharedPreferences.Editor widgetPrefsEditor = widgetPrefs.edit();
        widgetPrefsEditor.putLong("rowId", id + 1);
        widgetPrefsEditor.commit();
        
        return id;
    }
	
	public static class TextChangeListener implements android.text.TextWatcher {
		private static Map<String, Editable> changed =
				new HashMap<String, Editable>();
		private EditText me;
		private Editable oldText;
		private String pref;
		
		public TextChangeListener(String pref, EditText me) {
			this.me = me;
			this.pref = pref;
		}
		
		public static Map<String, Editable> getPrefs() {
			return changed;
		}
		
		@Override
		public void afterTextChanged(Editable s) {
			int type = me.getInputType();
			String text;
			switch (type) {
				case InputType.TYPE_CLASS_TEXT:
					changed.put(pref, s);
					break;
				case InputType.TYPE_CLASS_NUMBER:
					text = s.toString();
					try {
						Integer.valueOf(text);
						changed.put(pref, s);
					} catch (NumberFormatException nfe) {
						s = oldText;
						me.setError("Not number");
					}
					break;
				case InputType.TYPE_CLASS_DATETIME:
					text = s.toString();
					SimpleDateFormat simpleDate = new SimpleDateFormat();
					try {
						simpleDate.parse(text);
						changed.put(pref, s);
					} catch (ParseException pe) {
						s = oldText;
						me.setError(pe.getMessage());
					}
				default:
					/* Treat input type as TYPE_CLASS_TEXT */
					changed.put(pref, s);
					break;
			}
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {
			oldText = me.getText();
		}

		@Override
		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
		}
		
	}
	
	public static class CheckboxChangeListener implements View.OnClickListener {
		private static Map<String, Boolean> changed =
				new HashMap<String, Boolean>();
		private CheckBox me;
		private String pref;
		private OnClickCallback callback = null;

		public CheckboxChangeListener(String pref, CheckBox me) {
			this.me = me;
			this.pref = pref;
		}

		@Override
		public void onClick(View v) {
			CheckBox checkBox = (CheckBox) v;
			changed.put(pref, checkBox.isChecked());
			if (callback != null)
				callback.callback(pref, checkBox);
		}
		
		public static Map<String, Boolean> getPrefs() {
			return changed;
		}
	
		public void setOnClickCallback(OnClickCallback callback) {
			this.callback = callback;
		}
	}

}
