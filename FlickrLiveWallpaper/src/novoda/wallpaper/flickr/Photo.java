package novoda.wallpaper.flickr;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.net.URLConnection;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

/*
 * Represents a single photo taken from the photo search method:
 * http://www.flickr.com/services/api/flickr.photos.search.html
 * 
 */
public class Photo {
	private long id;
	private String owner;
	private String secret;
	private int server;
	private int farm;
	private String title;
	private boolean isPublic;
	private boolean isFriend;
	private boolean isFamily;

	private final String URL_FORMAT = "http://farm%d.static.flickr.com/%d/%d_%s.jpg";

	public Photo(int id, String owner, String secret, int server, int farm,
			String title, boolean isPublic, boolean isFriend, boolean isFamily) {
		super();
		this.id = id;
		this.owner = owner;
		this.secret = secret;
		this.server = server;
		this.farm = farm;
		this.title = title;
		this.isPublic = isPublic;
		this.isFriend = isFriend;
		this.isFamily = isFamily;
	}

	protected Photo() {
	}

	public String getPhotoURL() {
		return String.format(URL_FORMAT, farm, server, id, secret);
	}
	
	public Bitmap getPhoto() throws IOException {
		URLConnection connection = null;
		connection = new URL(getPhotoURL()).openConnection();
		BufferedInputStream bin = new BufferedInputStream(connection
				.getInputStream(), 1024);
		return BitmapFactory.decodeStream(bin);
	}

	/**
	 * Gets a new Photo object from XML
	 * 
	 * @param photoTag
	 *            the XML representation of a photo
	 * @return a photo object from the XML string or an empty object if an error
	 *         occurs
	 */
	public static Photo fromXML(String photoTag) {
		Photo photo = new Photo();
		if (photoTag == null)
			return photo;
		XmlPullParserFactory factory;
		try {
			factory = XmlPullParserFactory.newInstance();
			factory.setNamespaceAware(true);
			XmlPullParser xpp = factory.newPullParser();
			xpp.setInput(new StringReader(photoTag));

			int eventType = xpp.getEventType();
			while (eventType != XmlPullParser.END_DOCUMENT) {
				if (eventType == XmlPullParser.START_TAG
						&& xpp.getName().equalsIgnoreCase("photo")) {
					int isFamily = new Integer(xpp.getAttributeValue(null,
							"isfamily"));
					int isFriend = new Integer(xpp.getAttributeValue(null,
							"isfriend"));
					int isPublic = new Integer(xpp.getAttributeValue(null,
							"ispublic"));

					photo.setId(new Long(xpp.getAttributeValue(null, "id")));
					photo.setFamily((isFamily == 0) ? false : true);
					photo.setFarm(new Integer(xpp.getAttributeValue(null,
							"farm")));
					photo.setFriend((isFriend == 0) ? false : true);
					photo.setOwner(xpp.getAttributeValue(null, "owner"));
					photo.setPublic((isPublic == 0) ? false : true);
					photo.setSecret(xpp.getAttributeValue(null, "secret"));
					photo.setServer(new Integer(xpp.getAttributeValue(null,
							"server")));
					photo.setTitle(xpp.getAttributeValue(null, "title"));
				}
				eventType = xpp.next();
			}
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return photo;
	}

	// can't get raw string from xpp so adapting badly with no DRY...
	// TODO make it DRY
	public static Photo fromXPP(XmlPullParser xpp) {
		Photo photo = new Photo();
		int isFamily = new Integer(xpp.getAttributeValue(null, "isfamily"));
		int isFriend = new Integer(xpp.getAttributeValue(null, "isfriend"));
		int isPublic = new Integer(xpp.getAttributeValue(null, "ispublic"));

		photo.setId(new Long(xpp.getAttributeValue(null, "id")));
		photo.setFamily((isFamily == 0) ? false : true);
		photo.setFarm(new Integer(xpp.getAttributeValue(null, "farm")));
		photo.setFriend((isFriend == 0) ? false : true);
		photo.setOwner(xpp.getAttributeValue(null, "owner"));
		photo.setPublic((isPublic == 0) ? false : true);
		photo.setSecret(xpp.getAttributeValue(null, "secret"));
		photo.setServer(new Integer(xpp.getAttributeValue(null, "server")));
		photo.setTitle(xpp.getAttributeValue(null, "title"));
		return photo;
	}

	/* getters and setters */

	public long getId() {
		return id;
	}

	public void setId(long id) {
		this.id = id;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public String getSecret() {
		return secret;
	}

	public void setSecret(String secret) {
		this.secret = secret;
	}

	public int getServer() {
		return server;
	}

	public void setServer(int server) {
		this.server = server;
	}

	public int getFarm() {
		return farm;
	}

	public void setFarm(int farm) {
		this.farm = farm;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public boolean isPublic() {
		return isPublic;
	}

	public void setPublic(boolean isPublic) {
		this.isPublic = isPublic;
	}

	public boolean isFriend() {
		return isFriend;
	}

	public void setFriend(boolean isFriend) {
		this.isFriend = isFriend;
	}

	public boolean isFamily() {
		return isFamily;
	}

	public void setFamily(boolean isFamily) {
		this.isFamily = isFamily;
	}
}
