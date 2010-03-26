package novoda.wallpaper.flickr;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;

import android.util.Log;
import android.util.Pair;

@SuppressWarnings("unchecked")
public abstract class Flickr<T> {

	public InputStream getContent() throws MalformedURLException, IOException {
		if (api == null || api.equals("")) {
			throw new MalformedURLException("An API is needed");
		}

		if (getMethod() == null || getMethod().equals("")) {
			throw new MalformedURLException("A method is needed");
		}

		URLConnection connection = null;
		connection = new URL(constructUrl(getArguments())).openConnection();
		BufferedInputStream bin = new BufferedInputStream(connection
				.getInputStream());
		return bin;
	}

	public Flickr<T> with(Pair pair) {
		arguments.add(pair);
		return this;
	}

	public Flickr<T> with(String key, String value) {
		return with(new Pair<String, String>(key, value));
	}

	public Flickr<T> remove(String key, String value) {
		arguments.remove(new Pair<String, String>(key, value));
		return this;
	}

	public String constructUrl(List<Pair> list) {
		if (list == null || list.size() == 0)
			return URL;

		StringBuilder builder = new StringBuilder(URL);
		builder.append('?').append("method=").append(getMethod()).append('&')
				.append("api_key=").append(api).append('&');
		for (Pair p : list) {
			builder.append(p.first.toString()).append('=').append(
					p.second.toString()).append('&');
		}
		builder.deleteCharAt(builder.length() - 1);
		Log.d(TAG, "Calling Flickr API with URL [" + builder.toString() + "]");
		return builder.toString();
	}

	public List<Pair> getArguments() {
		return arguments;
	}

	public abstract List<T> fetchStructuredDataList();

	public abstract String getMethod();

	private static final String TAG = Flickr.class.getSimpleName();
	private final String URL = "http://api.flickr.com/services/rest/";
	private String api = "655ba9dc4418959a43a9c37aa4acea49";
	private List<Pair> arguments = new ArrayList<Pair>();

}
