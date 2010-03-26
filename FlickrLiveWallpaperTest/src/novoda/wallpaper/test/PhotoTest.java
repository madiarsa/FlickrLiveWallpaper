package novoda.wallpaper.test;

import novoda.wallpaper.flickr.Photo;
import junit.framework.TestCase;

public class PhotoTest extends TestCase {

	private final String PHOTO = "<photo id=\"4341987094\" "
			+ "owner=\"41073876@N00\"" + "secret=\"7528f197ac\" "
			+ "server=\"4001\" " + "farm=\"5\"" + " title=\"IMG_8610\""
			+ " ispublic=\"1\" " + "isfriend=\"0\"" + " isfamily=\"0\"/>";
	
	private Photo photo;

	private final String URL = "http://farm5.static.flickr.com/4001/4341987094_7528f197ac_b.jpg";
	
	protected void setUp() throws Exception {
		super.setUp();
		photo = Photo.fromXML(PHOTO);
	}

	public void testShouldParseAPhotoXML() throws Exception {
		assertEquals(4341987094L, photo.id);
		assertEquals("41073876@N00", photo.owner);
		assertTrue(photo.isPublic);
		assertFalse(photo.isFriend);
	}
	
	public void testGettingPhotoURL() throws Exception {
		assertEquals(URL, photo.getPhotoURL(false));
	}
}
