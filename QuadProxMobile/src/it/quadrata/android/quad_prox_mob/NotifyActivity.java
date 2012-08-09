package it.quadrata.android.quad_prox_mob;

import it.quadrata.android.quad_prox_mob.WidgetPrefsActivity.CheckboxChangeListener;

import java.util.Map;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Toast;

public class NotifyActivity extends Activity implements OnClickCallback {
    private Map<String, String> prefs;
    private CheckBox notifyChange;
    private CheckBox notifyCpu;
    private CheckBox notifyMem;
    private CheckBox notifyEnable;
    private EditText cpuLimit;
    private EditText memLimit;
    
	public void onCreate(Bundle savedInstanceState) {
		String value;
		
        super.onCreate(savedInstanceState);
        setContentView(R.layout.notify_layout);

		Intent intent = getIntent();
		int widgetId = intent.getIntExtra("widgetId", AppWidgetManager.INVALID_APPWIDGET_ID);
		prefs = WidgetPrefsActivity.getPrefs(NotifyActivity.this, widgetId);
		
		notifyChange = (CheckBox) findViewById(R.id.notifyChange);
		value = prefs.get("notifyChange");
		if (value != null) {
			if (value.equals("true")) {
				notifyChange.setChecked(true);
			}
			else {
				notifyChange.setChecked(false);
			}
		}
		else {
			notifyChange.setChecked(false);
		}
		CheckboxChangeListener changeListener = 
			new WidgetPrefsActivity.CheckboxChangeListener("notifyChange", notifyChange);
		notifyChange.setOnClickListener(changeListener);
		changeListener.setOnClickCallback(this);
		
		notifyCpu = (CheckBox) findViewById(R.id.notifyCpu);
		cpuLimit = (EditText) findViewById(R.id.cpuLimit);
		cpuLimit.setText(prefs.get("cpuLimit"));
		value = prefs.get("notifyCpu");
		if (value != null) {
			if (value.equals("true")) {
				notifyCpu.setChecked(true);
				cpuLimit.setEnabled(true);
			}
			else {
				notifyCpu.setChecked(false);
				cpuLimit.setEnabled(false);
			}
		}
		else {
			notifyCpu.setChecked(false);
			cpuLimit.setEnabled(false);
		}
		CheckboxChangeListener cpuListener = 
				new WidgetPrefsActivity.CheckboxChangeListener("notifyCpu", notifyCpu);
		notifyCpu.setOnClickListener(cpuListener);
		cpuListener.setOnClickCallback(this);
		cpuLimit.addTextChangedListener(new WidgetPrefsActivity.TextChangeListener(
				"cpuLimit", cpuLimit));
		
		notifyMem = (CheckBox) findViewById(R.id.notifyMem);
		memLimit = (EditText) findViewById(R.id.memLimit);
		memLimit.setText(prefs.get("memLimit"));
		value = prefs.get("notifyMem");
		if (value != null) {
			if (value.equals("true")) {
				notifyMem.setChecked(true);
				memLimit.setEnabled(true);
			}
			else {
				notifyMem.setChecked(false);
				memLimit.setEnabled(false);
			}
		}
		else {
			notifyMem.setChecked(false);
			memLimit.setEnabled(false);
		}
		CheckboxChangeListener memListener = 
				new WidgetPrefsActivity.CheckboxChangeListener("notifyMem", notifyMem);
		notifyMem.setOnClickListener(memListener);
		memListener.setOnClickCallback(this);
		memLimit.addTextChangedListener(new WidgetPrefsActivity.TextChangeListener(
				"memLimit", memLimit));
	
		notifyEnable = (CheckBox) findViewById(R.id.notifyEnable);
		value = prefs.get("notifyEnable");
		if (value != null) {
			if (value.equals("true")) {
				notifyEnable.setChecked(true);
				notifyChange.setEnabled(true);
				notifyCpu.setEnabled(true);
				notifyMem.setEnabled(true);
			}
			else {
				disableDependant();
			}
		}
		else {
			disableDependant();
		}
		CheckboxChangeListener notifyListener = 
				new WidgetPrefsActivity.CheckboxChangeListener("notifyEnable", notifyCpu);
		notifyEnable.setOnClickListener(notifyListener);
		notifyListener.setOnClickCallback(this);
		
	}

	@Override
	public void callback(String pref, CheckBox object) {
		if (pref.equals("notifyEnable")) {
			notifyChange.setEnabled(object.isChecked());
			notifyCpu.setEnabled(object.isChecked());
			notifyMem.setEnabled(object.isChecked());
			if (object.isChecked() == false) {
				disableDependant();
			}
		}
		else if (pref.equals("notifyChange")) {
			
		}
		else if (pref.equals("notifyCpu")) {
			cpuLimit.setEnabled(object.isChecked());
		}
		else if (pref.equals("notifyMem")) {
			memLimit.setEnabled(object.isChecked());
		}
	}
	
	private void disableDependant() {
		if (notifyChange.isChecked())
			notifyChange.performClick();
		if (notifyCpu.isChecked())
			notifyCpu.performClick();
		if (notifyMem.isChecked())
			notifyMem.performClick();
		memLimit.setEnabled(false);
		cpuLimit.setEnabled(false);
		notifyChange.setEnabled(false);
		notifyCpu.setEnabled(false);
		notifyMem.setEnabled(false);
	}
	
}
