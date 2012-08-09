package it.quadrata.android.quad_prox_mob;

import java.util.Map;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

public class PrefsActivity extends Activity {
	private Map<String, String> prefs;
	private EditText fontSize;
	private EditText interval;
	
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.prefs_layout);

		Intent intent = getIntent();
		int widgetId = intent.getIntExtra("widgetId", AppWidgetManager.INVALID_APPWIDGET_ID);
		prefs = WidgetPrefsActivity.getPrefs(PrefsActivity.this, widgetId);
		
		fontSize = (EditText) findViewById(R.id.fontSize);
		fontSize.setText(prefs.get("fontSize"));
		fontSize.addTextChangedListener(
			new WidgetPrefsActivity.TextChangeListener("fontSize", fontSize));

		interval = (EditText) findViewById(R.id.interval);
		interval.setText(prefs.get("interval"));
		interval.addTextChangedListener(
			new WidgetPrefsActivity.TextChangeListener("interval", interval));
    }
    
}
