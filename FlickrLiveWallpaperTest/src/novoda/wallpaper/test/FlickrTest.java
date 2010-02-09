package novoda.wallpaper.test;

import java.util.ArrayList;
import java.util.List;

import junit.framework.TestCase;
import novoda.wallpaper.flickr.Flickr;
import novoda.wallpaper.flickr.Photo;
import android.util.Pair;

public class FlickrTest extends TestCase {

	private static final String EX1 = "http://api.flickr.com/services/rest/?method=flickr.photos.search" +
			"&api_key=655ba9dc4418959a43a9c37aa4acea49" +
			"&accuracy=11" +
			"&lat=38.898556&lon=-77.037852";

	public void testCreateUrls() throws Exception {
		Flickr f = new Search();
		assertEquals(EX1,f.constructUrl(f.getArguments()));
	}
	
	private class Search extends Flickr<Photo>{

		@Override
		public List<Photo> fetchStructuredDataList() {
			return null;
		}

		@Override
		public List<Pair> getArguments() {
			List<Pair> l = new ArrayList<Pair>();
			l.add(new Pair<String, String>("accuracy", "11"));
			l.add(new Pair<String, String>("lat", "38.898556"));
			l.add(new Pair<String, String>("lon", "-77.037852"));
			return l;
		}

		@Override
		public String getMethod() {
			return "flickr.photos.search";
		}		
	}
}
