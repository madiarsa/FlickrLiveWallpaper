package novoda.net;

import java.io.IOException;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.HttpVersion;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.codehaus.jackson.JsonNode;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.ObjectMapper;

import android.util.Log;

public class GeoNamesAPI {

	static {
		setupHttpClient();
	}

	/*
	 * Gracefully taken from droidfu - rockon Mathias
	 * github.com/kaeppler/droid-fu/
	 */
	private static void setupHttpClient() {
		BasicHttpParams httpParams = new BasicHttpParams();
		ConnManagerParams.setTimeout(httpParams, CONNECTION_TIMEOUT);
		ConnManagerParams.setMaxConnectionsPerRoute(httpParams,
				new ConnPerRouteBean(MAX_CONNECTIONS));
		ConnManagerParams.setMaxTotalConnections(httpParams, MAX_CONNECTIONS);
		HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
		HttpProtocolParams.setUserAgent(httpParams, HTTP_USER_AGENT);
		SchemeRegistry schemeRegistry = new SchemeRegistry();
		schemeRegistry.register(new Scheme("http", PlainSocketFactory
				.getSocketFactory(), 80));
		schemeRegistry.register(new Scheme("https", PlainSocketFactory
				.getSocketFactory(), 443));
		ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(
				httpParams, schemeRegistry);
		httpClient = new DefaultHttpClient(cm, httpParams);
	}

	public String getNearestPlaceName(String lat, String lon) {
		final HttpGet get = new HttpGet(
				"http://ws.geonames.org/findNearbyPlaceNameJSON?lat=" + lat
						+ "&lng=" + lon);

		HttpEntity entity = null;
		JsonNode array = null;
		try {
			final HttpResponse response = httpClient.execute(get);
			if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				array = handleResponse(response);
				array = array.path("geonames").get(0);
			}
		} catch (ClientProtocolException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (entity != null) {
				try {
					entity.consumeContent();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		return array.path("name").getTextValue();
	}

	public JsonNode handleResponse(HttpResponse response)
			throws ClientProtocolException, IOException {
		BufferedHttpEntity ent = new BufferedHttpEntity(response.getEntity());
		JsonNode array = null;
		try {
			array = mapper.readTree(ent.getContent());
			Log.i(TAG, "JSON: " + array.toString());
		} catch (JsonParseException e) {
			Log.e(TAG, "parsing error: " + e.getMessage());
			try {
				Log
						.i(TAG,
								"Avoiding printing out the data incase spacial chars crash logcat");
			} catch (Exception e2) {
				Log.e(TAG, "can't read stream");
			}
		}

		return array;
	}

	private static ObjectMapper mapper = new ObjectMapper();

	private static final String TAG = GeoNamesAPI.class.getSimpleName();

	private static final int MAX_CONNECTIONS = 6;

	private static final int CONNECTION_TIMEOUT = 10 * 1000;

	protected static final String HTTP_CONTENT_TYPE_HEADER = "Content-Type";

	protected static final String HTTP_USER_AGENT = "Android/RESTProvider";

	protected static AbstractHttpClient httpClient;
}
