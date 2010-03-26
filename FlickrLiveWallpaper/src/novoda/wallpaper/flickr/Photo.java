package novoda.wallpaper.flickr;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

/*
 * Represents a single photo taken from the photo search method:
 * http://www.flickr.com/services/api/flickr.photos.search.html
 * 
 */
public class Photo {

	@Override
	public String toString() {
		StringBuffer buff = new StringBuffer();
	    Field fields[] = this.getClass().getDeclaredFields();
	    try {
		    for (int i = 0; i < fields.length; i++){ 
		       fields[i].setAccessible(true); 
				buff.append(fields[i].getName() + "=[" + fields[i].get(this) +"]");
		    }
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (IllegalAccessException e) {
			e.printStackTrace();
	    }
		return buff.toString();
	}

	private static final String TAG = Photo.class.getSimpleName();

	public long id;
	public String owner;
	public String secret;
	public int server;
	public int farm;
	public String title;
	public boolean isPublic;
	public boolean isFriend;
	public boolean isFamily;
	public String hiResImg_url;
	public int hiResImg_height;
	public int hiResImg_width;

	private final String URL_FORMAT = "http://farm%d.static.flickr.com/%d/%d_%s_b.jpg";
	public String smallResImg_url;
	public Integer smallResImg_height;
	public Integer smallResImg_width;
	public Integer medResImg_height;
	public String medResImg_url;
	public Integer medResImg_width;

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

	public String getPhotoURL(boolean original) {
		if (hiResImg_url != null && !hiResImg_url.equals("") && original) {
			Log.i(TAG, "Hi Res URL = " + hiResImg_url);
			return hiResImg_url;
		} else {
			Log.i(TAG, "Formatted URL = " + String.format(URL_FORMAT, farm, server, id, secret));
			return String.format(URL_FORMAT, farm, server, id, secret);
		}
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
		
		Log.d(TAG, "Parsing xml photo ["+photoTag+"]");
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
					int isFamily = new Integer(xpp.getAttributeValue(null, "isfamily"));
					int isFriend = new Integer(xpp.getAttributeValue(null, "isfriend"));
					int isPublic = new Integer(xpp.getAttributeValue(null, "ispublic"));

					photo.id = new Long(xpp.getAttributeValue(null, "id"));
					photo.isFamily = (isFamily == 0) ? false : true;
					photo.farm = new Integer(xpp.getAttributeValue(null,	"farm"));
					photo.isFriend = ((isFriend == 0) ? false : true);
					photo.owner = (xpp.getAttributeValue(null, "owner"));
					photo.isPublic = ((isPublic == 0) ? false : true);
					photo.secret = (xpp.getAttributeValue(null, "secret"));
					photo.server = (new Integer(xpp.getAttributeValue(null,	"server")));
					photo.title = (xpp.getAttributeValue(null, "title"));
					photo.hiResImg_url = (xpp.getAttributeValue(null, "url_o"));
					photo.hiResImg_height = (new Integer(xpp.getAttributeValue(null, "height_o")));
					photo.hiResImg_width = (new Integer(xpp.getAttributeValue(null,	"width_o")));
				}
				eventType = xpp.next();
			}
		} catch (XmlPullParserException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NumberFormatException e) {
			// bail;
		}
		return photo;
	}

	// can't get raw string from xpp so adapting badly with no DRY...
	// TODO make it DRY
	public static Photo fromXPP(XmlPullParser xpp) {
		Log.i(TAG, "Adding Photo from XPP");
		for(int i=0;i<xpp.getAttributeCount();i++){
			Log.i(TAG, "Attribute[" + xpp.getAttributeName(i)+"]="+ xpp.getAttributeValue(i));
			
		}
		
		Log.d(TAG, "--");
		Photo photo = new Photo();
		try {
			int isFamily = new Integer(xpp.getAttributeValue(null, "isfamily"));
			int isFriend = new Integer(xpp.getAttributeValue(null, "isfriend"));
			int isPublic = new Integer(xpp.getAttributeValue(null, "ispublic"));

			photo.id = new Long(xpp.getAttributeValue(null, "id"));
			photo.isFamily =(isFamily == 0) ? false : true;
			photo.farm =new Integer(xpp.getAttributeValue(null, "farm"));
			photo.isFriend = (isFriend == 0) ? false : true;
			photo.owner = (xpp.getAttributeValue(null, "owner"));
			photo.isPublic = ((isPublic == 0) ? false : true);
			photo.secret = (xpp.getAttributeValue(null, "secret"));
			photo.server = (new Integer(xpp.getAttributeValue(null, "server")));
			photo.title = (xpp.getAttributeValue(null, "title"));

			//
			photo.smallResImg_url= (xpp.getAttributeValue(null, "url_s"));
			photo.smallResImg_height = (new Integer(xpp.getAttributeValue(null,"height_s")));
			photo.smallResImg_width=(new Integer(xpp.getAttributeValue(null,"width_s")));
			
			photo.medResImg_url= (xpp.getAttributeValue(null, "url_m"));
			photo.medResImg_height = (new Integer(xpp.getAttributeValue(null,"height_m")));
			photo.medResImg_width=(new Integer(xpp.getAttributeValue(null,"width_m")));
			
			photo.hiResImg_url= (xpp.getAttributeValue(null, "url_o"));
			photo.hiResImg_height = (new Integer(xpp.getAttributeValue(null,"height_o")));
			photo.hiResImg_width=(new Integer(xpp.getAttributeValue(null,"width_o")));
		} catch (NumberFormatException e) {
			// bail
		}
		
		return photo;
	}
	
}
