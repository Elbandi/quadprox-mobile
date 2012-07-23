package it.quadrata.android.quad_prox_mob;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class AuthActivity extends Activity {

	// // Log tag
	// private final static String LOG_TAG = "AuthActivity";

	// Authentication credentials
	private static String server;
	private static String username;
	private static String realm;
	private static String password;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.auth_layout);

		final EditText serverInput = (EditText) findViewById(R.id.serverInput);
		final EditText usernameInput = (EditText) findViewById(R.id.usernameInput);
		final EditText realmInput = (EditText) findViewById(R.id.realmInput);
		final EditText passwordInput = (EditText) findViewById(R.id.passwordInput);

		final SharedPreferences authPref = getSharedPreferences("AuthPref",
				Context.MODE_PRIVATE);
		serverInput.setText(authPref.getString("server", null));
		usernameInput.setText(authPref.getString("username", null));
		realmInput.setText(authPref.getString("realm", null));
		passwordInput.setText(authPref.getString("password", null));

		// Login button
		Button loginButton = (Button) findViewById(R.id.loginButton);
		loginButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Editable serverEdit = serverInput.getText();
				server = serverEdit.toString();
				Editable usernameEdit = usernameInput.getText();
				username = usernameEdit.toString();
				Editable realmEdit = realmInput.getText();
				realm = realmEdit.toString();
				Editable passwordEdit = passwordInput.getText();
				password = passwordEdit.toString();

				// Saving authentication fields to preference
				SharedPreferences.Editor authPrefEditor = authPref.edit();
				if ((server != null) && (username != null) && (realm != null)
						&& (password != null)) {
					authPrefEditor.putString("server", server);
					authPrefEditor.putString("username", username);
					authPrefEditor.putString("realm", realm);
					authPrefEditor.putString("password", password);
					authPrefEditor.commit();
					Intent vmListIntent = new Intent(AuthActivity.this,
							VMListActivity.class);
					// Putting authentication data into the intent for
					// VM list activity
					vmListIntent.putExtra("server", server);
					vmListIntent.putExtra("username", username);
					vmListIntent.putExtra("realm", realm);
					vmListIntent.putExtra("password", password);
					setResult(RESULT_OK, vmListIntent);
					finish();
				}
			}
		});
	}
}