package novoda.wallpaper.test;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;

import novoda.wallpaper.flickr.Photo;
import novoda.wallpaper.flickr.PhotoSearch;
import android.test.InstrumentationTestCase;

public class PhotoSearchTest extends InstrumentationTestCase {

	public void testCreateStructuredData() throws Exception {
		MockObj m = new MockObj();
		assertEquals(250, m.fetchStructuredDataList().size());
		Photo photo = m.fetchStructuredDataList().get(3);
		assertEquals("41073876@N00", photo.getOwner());
	}

	private class MockObj extends PhotoSearch {
		public InputStream getContent() throws MalformedURLException,
				IOException {
			return getInstrumentation().getContext().getAssets().open(
					"photosearch.xml");
		}
	}
}
