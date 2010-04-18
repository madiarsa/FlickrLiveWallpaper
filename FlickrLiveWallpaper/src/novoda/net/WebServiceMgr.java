package novoda.net;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;

import novoda.wallpaper.flickr.FlickrLiveWallpaper;

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
import org.apache.http.impl.client.AbstractHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpProtocolParams;

import android.util.Log;

public class WebServiceMgr {

    static {
        setupHttpClient();
    }
    
    private static final int CONNECTION_TIMEOUT = 10 * 1000;
    private static final int MAX_CONNECTIONS = 6;
    protected static final String HTTP_USER_AGENT = "Android/FlickerLiveWallpaper";
    
    private static AbstractHttpClient httpClient;
    public static final String TAG = FlickrLiveWallpaper.class.getSimpleName();
    
    private static void setupHttpClient() {
        BasicHttpParams httpParams = new BasicHttpParams();

        ConnManagerParams.setTimeout(httpParams, CONNECTION_TIMEOUT);
        ConnManagerParams.setMaxConnectionsPerRoute(httpParams, new ConnPerRouteBean(MAX_CONNECTIONS));
        ConnManagerParams.setMaxTotalConnections(httpParams, MAX_CONNECTIONS);
        HttpProtocolParams.setVersion(httpParams, HttpVersion.HTTP_1_1);
        HttpProtocolParams.setUserAgent(httpParams, HTTP_USER_AGENT);

        SchemeRegistry schemeRegistry = new SchemeRegistry();
        schemeRegistry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));

        ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(httpParams, schemeRegistry);
        httpClient = new DefaultHttpClient(cm, httpParams);
    }
    
    HttpResponse getHTTPResponse(URL photoUrl) {
        HttpGet request = null;
        HttpResponse response = null;

        try {
            request = new HttpGet(photoUrl.toURI());
        } catch (URISyntaxException e) {
            Log.e(TAG, "Could not create GetRequest: " + e.getMessage(), e);
        }

//      /*
//      * HACK: Related to Android #Issue 6850 HttpURLConnection has
//      * provided mixed reliability in Android and so looping in this
//      * manor ensures a good connection if it is available. An
//      * alternative is setting System.setProperty("http.keepAlive",
//      * "false"); but I did not see results from this.
//      */
        System.setProperty("http.keepAlive", "false");
        try {
            response = httpClient.execute(request);
        } catch (ClientProtocolException e) {
            Log.e(TAG, "Client Protocol exception: " + e.getMessage(), e);
        } catch (IOException e) {
            Log.e(TAG, "IOException exception: " + e.getMessage(), e);
        }
        
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            Log.i(TAG, "Response code:[" + HttpStatus.SC_OK + "] Msg:["
                    + response.getStatusLine().getReasonPhrase() + "] Type:["
                    + response.getEntity().getContentType() + "] length:["
                    + response.getEntity().getContentLength() + "]");
            
//            List<Header> li = Arrays.asList(response.getAllHeaders());
//            for(Header head : li){
//                Log.i(TAG, "Header: " + head.getName() + " values: " + head.getValue());
//            }
//            
        } else {
            Log.e(TAG, "Unsuccessful Connection response: " + response.getStatusLine().getStatusCode());
        }
        return response;
    }
    
}
