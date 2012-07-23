package it.quadrata.android.quad_prox_mob;

import java.security.KeyStore;

import org.apache.http.HttpVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;

import android.app.Application;
import android.util.Log;

public class ProxmoxCustomApp extends Application {

	// Log tag
	private final static String LOG_TAG = "HttpClient";

	// HttpClient reference
	private HttpClient httpClient;

	@Override
	public void onCreate() {
		super.onCreate();
		// HttpClient reference initialization
		httpClient = createHttpClient();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		Log.i(LOG_TAG, "Low Memory!");
		// Low memory resources release
		releaseHttpClient();
	}

	@Override
	public void onTerminate() {
		super.onTerminate();
		Log.i(LOG_TAG, "Terminate HttpClient");
		// On terminate resources release
		releaseHttpClient();
	}

	/**
	 * @return HttpClient reference
	 */
	public HttpClient getHttpClient() {
		return httpClient;
	}

	// HttpClient creation
	private final HttpClient createHttpClient() {
		try {
			KeyStore trustStore = KeyStore.getInstance(KeyStore
					.getDefaultType());
			trustStore.load(null, null);
			SSLSocketFactory noCertSocket = new ProxmoxCustomSocket(trustStore);
			noCertSocket
					.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
			HttpParams httpParams = new BasicHttpParams();
			HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
			HttpProtocolParams.setContentCharset(httpParams,
					HTTP.DEFAULT_CONTENT_CHARSET);
			SchemeRegistry schemeRegistry = new SchemeRegistry();
			Scheme httpScheme = new Scheme("http",
					PlainSocketFactory.getSocketFactory(), 80);
			schemeRegistry.register(httpScheme);
			Scheme httpsScheme = new Scheme("https", noCertSocket, 443);
			schemeRegistry.register(httpsScheme);
			ClientConnectionManager connManager = new ThreadSafeClientConnManager(
					httpParams, schemeRegistry);
			HttpClient tmpClient = new DefaultHttpClient(connManager,
					httpParams);
			Log.i(LOG_TAG, "HttpClient created!");
			return tmpClient;
		} catch (Exception e) {
			return new DefaultHttpClient();
		}
	}

	private final void releaseHttpClient() {
		if (httpClient != null && httpClient.getConnectionManager() != null) {
			// If a ConnectionManager exists it will be closed
			httpClient.getConnectionManager().shutdown();
			Log.i(LOG_TAG, "Releasing Connections");
		}
	}

}
