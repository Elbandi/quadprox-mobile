package it.quadrata.android.quad_prox_mob;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Spinner;

public class AccountsActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.accounts_layout);

        // Retrieving of login preferences
		Context context = AccountsActivity.this;
		String cluster = WidgetConfigActivity.getClusterName(context);
		if (cluster == null || cluster.length() == 0) {
			cluster = "No Name";
		}
		String[] items = new String[] {cluster};
		Spinner spinner = (Spinner) findViewById(R.id.cluster);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, items);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinner.setAdapter(adapter);
    }
}
