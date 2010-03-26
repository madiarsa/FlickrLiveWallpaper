package novoda.wallpaper.flickr;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import novoda.net.Flickr;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

public class PhotoSearch extends Flickr<Photo> {

	@Override
	public List<Photo> fetchStructuredDataList() {
		List<Photo> ret = new ArrayList<Photo>();
		XmlPullParserFactory factory;
		try {
			factory = XmlPullParserFactory.newInstance();

			factory.setNamespaceAware(true);
			final XmlPullParser xpp = factory.newPullParser();

			xpp.setInput(getContent(), null);
			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG) {
					final String name = xpp.getName();
					if (name.equals("rsp")
							&& xpp.getAttributeValue(null, "stat").equals(
									"fail")) {
						// failure return empty list
						return ret;
					} else if (name.equals("photo")) {
						ret.add(Photo.fromXPP(xpp));
					}
				}
				eventType = xpp.next();
			}
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return ret;
	}

	@Override
	public String getMethod() {
		return "flickr.photos.search";
	}

	private static final String TAG = PhotoSearch.class.getSimpleName();
}
