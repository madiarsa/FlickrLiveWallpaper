package novoda.wallpaper;

import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

import novoda.net.Flickr;
import novoda.net.GeoNamesAPI;
import novoda.wallpaper.flickr.Photo;
import novoda.wallpaper.flickr.PhotoSearch;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

public class FlickrService extends WallpaperService {

	@Override
	public Engine onCreateEngine() {
		return new FlickrEngine();
	}

	class FlickrEngine extends Engine implements
			SharedPreferences.OnSharedPreferenceChangeListener {

		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);
			Display dm = ((WindowManager) getSystemService(WINDOW_SERVICE))
					.getDefaultDisplay();
			displayWidth = dm.getWidth();
			displayHeight = dm.getHeight();
			displayMiddleX = displayWidth * 0.5f;

			mPrefs = FlickrService.this.getSharedPreferences(SHARED_PREFS_NAME,
					0);
			mPrefs.registerOnSharedPreferenceChangeListener(this);
			onSharedPreferenceChanged(mPrefs, null);
			geoNamesAPI = new GeoNamesAPI();
		}

		@Override
		public Bundle onCommand(String action, int x, int y, int z,
				Bundle extras, boolean resultRequested) {
			if (action.equals(WallpaperManager.COMMAND_TAP) && photo != null
					&& !photo.hiResImg_url.equalsIgnoreCase("")) {
				final Intent intent = new Intent(Intent.ACTION_VIEW, Uri
						.parse(photo.hiResImg_url));
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);

			}
			return super.onCommand(action, x, y, z, extras, resultRequested);
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			boolean reSynchNeeded = (System.currentTimeMillis() - lastSync) > 1000 * 60 * 60;
			currentlyVisibile = visible;
			if (visible) {
				if (reSynchNeeded) {
					mHandler.post(mDrawWallpaper);
					lastSync = System.currentTimeMillis();
				}
			} else {
				mHandler.removeCallbacks(mDrawWallpaper);
			}
		}

		@Override
		public void onDestroy() {
			if (cachedBitmap != null) {
				cachedBitmap.recycle();
			}
			photo = null;
			cachedBitmap = null;
			mHandler.removeCallbacks(mDrawWallpaper);
			super.onDestroy();
		}

		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			String scaleSetting = sharedPreferences.getString(PREF_SCALE_TYPE,
					PREF_SCALE_TYPE_MIDDLE);

			boolean beforePrefCalled = alignImgInMiddle;

			if (scaleSetting.equals(PREF_SCALE_TYPE_FULL)) {
				alignImgInMiddle = false;
			} else {
				alignImgInMiddle = true;
			}

			if (!(alignImgInMiddle == beforePrefCalled)) {
				Log.i(TAG, "pref changed");
				mHandler.post(mDrawWallpaper);
			}
		}

		private void drawCanvas() {
			Log.d(TAG, "Drawing Canvas");
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				if (c != null && cachedBitmap != null) {
					c.drawBitmap(cachedBitmap, 0, cachedTopMargin, new Paint());
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		private void drawInitialNotification() {
			Log.d(TAG, "Displaying loading info");
			float x = displayMiddleX;
			float y = 180;
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				Paint paint = new Paint();
				Bitmap decodeResource = BitmapFactory.decodeResource(
						getResources(), getResources().getIdentifier(
								"ic_logo_flickr", "drawable",
								"novoda.wallpaper"));
				c.drawBitmap(decodeResource,
						(x - decodeResource.getWidth() * 0.5f), y, paint);
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(Color.WHITE);
				paint.setTextSize(37);
				paint.setStyle(Paint.Style.STROKE);
				Typeface typeFace = Typeface.createFromAsset(getBaseContext()
						.getAssets(), "fonts/ArnoProRegular10pt.otf");
				paint.setTypeface(typeFace);
				c.translate(0, 30);
				paint.setTextAlign(Paint.Align.CENTER);

				if (c != null) {
					c.drawText("Downloading Image", x, y + 80, paint);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		private void drawDetailedLoadingNotification(String placeName) {
			Log.d(TAG, "Displaying loading details for placename=[" + placeName +"]");
			float x = displayMiddleX;
			float y = 180;
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				Paint paint = new Paint();
				Bitmap decodeResource = BitmapFactory.decodeResource(
						getResources(), getResources().getIdentifier(
								"ic_logo_flickr", "drawable",
								"novoda.wallpaper"));
				c.drawBitmap(decodeResource,
						(x - decodeResource.getWidth() * 0.5f), y, paint);
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(Color.WHITE);
				paint.setTextSize(37);
				paint.setStyle(Paint.Style.STROKE);
				Typeface typeFace = Typeface.createFromAsset(getBaseContext()
						.getAssets(), "fonts/ArnoProRegular10pt.otf");
				paint.setTypeface(typeFace);
				c.translate(0, 30);
				paint.setTextAlign(Paint.Align.CENTER);

				if (c != null) {
					c.drawText("Downloading Image", x, y + 80, paint);
					drawTextInRect(c, paint, new Rect((int) x, (int) y + 200,
							700, 300), "Looking for images around " + placeName);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}
		private void drawErrorNotification(String error) {
			Log.e(TAG, error);
			float x = displayMiddleX;
			float y = 180;
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				Paint paint = new Paint();
				Bitmap decodeResource = BitmapFactory.decodeResource(
						getResources(), getResources().getIdentifier(
								"ic_smile_sad_48", "drawable",
						"novoda.wallpaper"));
				c.drawBitmap(decodeResource, (x - decodeResource.getWidth() * 0.5f), y, paint);
				paint = new Paint();
				paint.setAntiAlias(true);
				paint.setColor(Color.WHITE);
				paint.setTextSize(37);
				paint.setStyle(Paint.Style.STROKE);
				Typeface typeFace = Typeface.createFromAsset(getBaseContext()
						.getAssets(), "fonts/ArnoProRegular10pt.otf");
				paint.setTypeface(typeFace);
				c.translate(0, 30);
				paint.setTextAlign(Paint.Align.CENTER);
				
				if (c != null) {
					drawTextInRect(c, paint, new Rect((int) x, (int) y + 80,700, 300), error);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		private void refreshImage(Location location, String placeName) {
			// List<Photo> list = getPhotosFromExactLocation(location);
			List<Photo> photos = getPhotosFromSurrounding(placeName, location);
			PhotoSpec<String, Object> photoSpecs = getBestSpecs(photos);

			if (photoSpecs != null) {
				cachedBitmap = retrievePhotoFromSpecs(photoSpecs);
			}
		}
		
		private Pair<Location, String> obtainLocation() throws ConnectException{
			Location location = getRecentLocation();
			return new Pair<Location, String>(location, findLocation(location));
		}

		private Bitmap retrievePhotoFromSpecs(
				PhotoSpec<String, Object> photoSpecs) {
			Bitmap original = null;
			URL photoUrl = null;

			try {
				photoUrl = new URL((String) photoSpecs
						.get(PhotoSpec.PHOTOSPEC_URL));
			} catch (MalformedURLException error) {
				error.printStackTrace();
			}

			try {
				HttpURLConnection connection = (HttpURLConnection) photoUrl
						.openConnection();
				connection.setDoInput(true);
				connection.connect();
				InputStream input = connection.getInputStream();
				original = BitmapFactory.decodeStream(input);
			} catch (IOException e) {
				e.printStackTrace();
			}

			if (original != null) {
				original = scaleImage(original, displayWidth, displayHeight);
			} else {
				Log.e(TAG, "Image returned from Service was null");
			}

			return original;
		}

		/**
		 * Should scale the bitmap to fit the height/width of the drawing
		 * canvas.
		 * 
		 * @param bitmap
		 * @param width
		 * @param height
		 * @return
		 */
		Bitmap scaleImage(Bitmap bitmap, int width, int height) {
			final int bitmapWidth = bitmap.getWidth();
			final int bitmapHeight = bitmap.getHeight();

			final float scale;

			if (alignImgInMiddle) {
				scale = Math.min((float) width / (float) bitmapWidth,
						(float) height / (float) bitmapHeight);
			} else {
				scale = Math.max((float) width / (float) bitmapWidth,
						(float) height / (float) bitmapHeight);
			}

			final int scaledWidth = (int) (bitmapWidth * scale);
			final int scaledHeight = (int) (bitmapHeight * scale);

			Log.d(TAG, "Scaling Bitmap (height x width): Orginal["
					+ bitmapHeight + "x" + bitmapWidth + "], New["
					+ scaledHeight + "x" + scaledWidth + "]");
			Bitmap createScaledBitmap = Bitmap.createScaledBitmap(bitmap,
					scaledWidth, scaledHeight, true);

			/*
			 * Work out the Top Margin to align the image in the middle of the
			 * screen with a slightly larger bottom gutter for framing
			 * screenDivisions = totalScreenHeight/BitmapHeight cachedTopMargin
			 * = screenDivisions - (BitmapHeight*0.5)
			 */
			if (alignImgInMiddle) {
				final float screenDividedByPic = Math.min(
						(float) displayHeight, (float) scaledHeight);
				cachedTopMargin = Math
						.round((screenDividedByPic - (float) scaledHeight * 0.5));
			} else {
				cachedTopMargin = 0;
			}

			return createScaledBitmap;
		}

		private Location getRecentLocation() {
			final LocationManager locManager = (LocationManager) FlickrService.this
					.getBaseContext()
					.getSystemService(Context.LOCATION_SERVICE);
			Location location = null;
			for (String provider : locManager.getProviders(true)) {
				location = locManager.getLastKnownLocation(provider);
				if (location != null) {
					break;
				}
			}
			return location;
		}

		/*
		 * From the suitable specs collected, one is chosen at random.
		 */
		private PhotoSpec<String, Object> getBestSpecs(List<Photo> photos) {
			Log.v(TAG, "Choosing a photo from amoungst those with URLs");
			PhotoSpec<String, Object> spec = new PhotoSpec<String, Object>();
			List<PhotoSpec<String, Object>> options = new ArrayList<PhotoSpec<String, Object>>();
			for (Photo p : photos) {
				if (p.hiResImg_url != null) {
					spec.put(PhotoSpec.PHOTOSPEC_HEIGHT, p.hiResImg_height);
					spec.put(PhotoSpec.PHOTOSPEC_WIDTH, p.hiResImg_width);
					spec.put(PhotoSpec.PHOTOSPEC_URL, p.hiResImg_url);
					options.add(spec);
					spec = new PhotoSpec<String, Object>();
				}

				if (p.medResImg_url != null) {
					spec.put(PhotoSpec.PHOTOSPEC_HEIGHT, p.medResImg_height);
					spec.put(PhotoSpec.PHOTOSPEC_WIDTH, p.medResImg_width);
					spec.put(PhotoSpec.PHOTOSPEC_URL, p.medResImg_url);
					options.add(spec);
					spec = new PhotoSpec<String, Object>();
				}

				if (p.smallResImg_url != null) {
					spec.put(PhotoSpec.PHOTOSPEC_HEIGHT, p.smallResImg_height);
					spec.put(PhotoSpec.PHOTOSPEC_WIDTH, p.smallResImg_width);
					spec.put(PhotoSpec.PHOTOSPEC_URL, p.smallResImg_url);
					options.add(spec);
					spec = new PhotoSpec<String, Object>();
				}
			}

			int opts = options.size();
			if (opts > 1) {
				opts = randomWheel.nextInt(opts - 1);
				return options.get(opts);
			}

			return options.get(0);
		}

		/*
		 * Use location to establish a place name via the GeoName API Query
		 * Flickr to establish if photos are available Requery if photos can be
		 * divided into pages (to help randomness)
		 */
		private List<Photo> getPhotosFromSurrounding(String placeNameTag,
				Location location) {

			// Add random to ensure varying results
			photoSearch.with("accuracy", "11");
			photoSearch.with("tags", placeNameTag);
			photoSearch.with("sort", "interestingness-desc");
			photoSearch.with("media", "photos");
			photoSearch.with("extras", "url_m");
			photoSearch.with("extras", "url_o");
			photoSearch.with("extras", "url_s");
			photoSearch.with("per_page", "50");
			List<Photo> list = photoSearch.fetchStructuredDataList();

			if (list.size() > 1) {
				int square = (int) Math.sqrt(list.size());
				photoSearch.with("per_page", "" + square);
				photoSearch.with("page", "" + randomWheel.nextInt(square - 1));
				list = photoSearch.fetchStructuredDataList();
			}

			return list;
		}

		private String findLocation(Location location) throws ConnectException{
			Log
					.d(TAG,
							"Requesting photo details based on approximate location");
			String place = geoNamesAPI.getNearestPlaceName(df.format(location
					.getLatitude()), df.format(location.getLongitude()));
			return place;
		}

		private List<Photo> getPhotosFromExactLocation(Location location) {
			Log.d(TAG, "Requesting photo details based on exact location");
			final Flickr<Photo> photoSearch = new PhotoSearch();

			double latitude = location.getLatitude();
			double longitude = location.getLongitude();

			// Random no. between 0.1 > 0.0099
			double d = randomWheel.nextDouble();
			d = Double.valueOf(df.format((d * (0.1 - 0.0099))));

			Log.i(TAG, "Original Longitude=[" + longitude + "] latitude=["
					+ latitude + "]");
			Log.i(TAG, "Ammended Longitude=[" + (df.format(longitude + d))
					+ "] latitude=[" + (df.format(latitude + d)) + "]");
			// Add random to ensure varying results

			photoSearch.with("lat", "" + df.format(latitude + d));
			photoSearch.with("lon", "" + df.format(longitude + d));
			photoSearch.with("accuracy", "1");
			photoSearch.with("tags", getPeriodOfDay(new GregorianCalendar()
					.get(Calendar.HOUR_OF_DAY)));
			photoSearch.with("sort", "interestingness-desc");
			photoSearch.with("media", "photos");
			photoSearch.with("extras", "url_m");
			photoSearch.with("per_page", "1");
			// f.with("page", ""+ randomWheel.nextInt(5));
			List<Photo> list = photoSearch.fetchStructuredDataList();

			if (list.size() < 1) {
				photoSearch.remove("accuracy", "16");
				photoSearch.with("accuracy", "11");
			}

			for (int i = 0; i < list.size(); i++) {
				Log.i(TAG, "Photo in list= " + list.get(i).toString());
			}

			return list;
		}

		/**
		 * Returns a human readable tag which will be used with the search
		 * query.
		 * 
		 * @param 24h clock format
		 * @return Period of Day
		 */
		String getPeriodOfDay(int time) {
			if ((time > 22 && time <= 24) || (time >= 0 && time <= 5)) {
				return "night";
			}
			if (time > 5 && time <= 7) {
				return "dawn";
			}
			if (time > 7 && time <= 11) {
				return "morning";
			}
			if (time > 11 && time <= 15) {
				return "noon";
			}
			if (time > 15 && time <= 19) {
				return "afternoon";
			}
			if (time > 19 && time <= 22) {
				return "evening";
			}
			// should not be here but just in case as it s getting late
			return "city";
		}

		/*
		 * TODO: There are rumoured better ways of wrapping text on canvas using a
		 * staticLayout
		 */
		private void drawTextInRect(Canvas canvas, Paint paint, Rect r,
				CharSequence text) {
		
			// initial text range and starting position
			int start = 0;
			int end = text.length() - 1;
			float x = r.left;
			float y = r.top;
			int allowedWidth = r.width();
		
			if (allowedWidth < 30) {
				return; // too small
			}
		
			int lineHeight = paint.getFontMetricsInt(null);
		
			// For each line, with word wrap on whitespace.
			while (start < end) {
				int charactersRemaining = end - start + 1;
				int charactersToRenderThisPass = charactersRemaining; // optimism!
				int extraSkip = 0;
				// This 'while' is nothing to be proud of.
				// This should probably be a binary search or more googling to
				// find "character index at distance N pixels in string"
				while (charactersToRenderThisPass > 0
						&& paint.measureText(text, start, start
								+ charactersToRenderThisPass) > allowedWidth) {
					charactersToRenderThisPass--;
				}
		
				// charactersToRenderThisPass would definitely fit, but could be
				// in the middle of a word
				int thisManyWouldDefinitelyFit = charactersToRenderThisPass;
				if (charactersToRenderThisPass < charactersRemaining) {
					while (charactersToRenderThisPass > 0
							&& !Character.isWhitespace(text.charAt(start
									+ charactersToRenderThisPass - 1))) {
						charactersToRenderThisPass--;
					}
				}
		
				// line breaks
				int i;
				for (i = 0; i < charactersToRenderThisPass; i++) {
					if (text.charAt(start + i) == '\n') {
						charactersToRenderThisPass = i;
						extraSkip = 1;
						break;
					}
				}
		
				if (charactersToRenderThisPass < 1 && (extraSkip == 0)) {
					// no spaces found, must be a really long word.
					// Panic and show as much as would fit, breaking the word in
					// the middle
					charactersToRenderThisPass = thisManyWouldDefinitelyFit;
				}
		
				// Emit this line of characters and advance our offsets for the
				// next line
				if (charactersToRenderThisPass > 0) {
					canvas.drawText(text, start, start
							+ charactersToRenderThisPass, x, y, paint);
				}
				start += charactersToRenderThisPass + extraSkip;
				y += lineHeight;
		
				// start had better advance each time through the while, or
				// we've invented an infinite loop
				if ((charactersToRenderThisPass + extraSkip) < 1) {
					return;
				}
			}
		}

		private final Runnable mDrawWallpaper = new Runnable() {
			public void run() {
				if (currentlyVisibile) {
					drawInitialNotification();
					
					try{
						location = obtainLocation();
					}catch(ConnectException e){
						location=null;
						drawErrorNotification("Could not connect to the internet to find your location");
					}
					
					if (location != null) {
						drawDetailedLoadingNotification(location.second);
						refreshImage(location.first, location.second);
						drawCanvas();
					}
					
				} else {
					// Waiting until wallpaper becomes visible
					mHandler.postDelayed(mDrawWallpaper, 600);
				}
			}
		};

		/*
		 * This class exists just to save the essentials of what is needed to
		 * deal with images being requested and cached.
		 */
		@SuppressWarnings("hiding")
		private class PhotoSpec<String, Object> extends HashMap<String, Object> {

			private static final long serialVersionUID = 1L;

			public final static java.lang.String PHOTOSPEC_URL = "url";

			public final static java.lang.String PHOTOSPEC_WIDTH = "width";

			public final static java.lang.String PHOTOSPEC_HEIGHT = "height";

		}

		private final Handler mHandler = new Handler();

		private static final String PREF_SCALE_TYPE_FULL = "full";

		private static final String PREF_SCALE_TYPE_MIDDLE = "middle";

		private static final String PREF_SCALE_TYPE = "flickr_scale";

		private Bitmap cachedBitmap = null;

		private int displayWidth;

		private int displayHeight;

		private Photo photo = null;

		private long lastSync = 0;

		private float cachedTopMargin = 0;

		private boolean alignImgInMiddle = true;

		private SharedPreferences mPrefs;

		private Random randomWheel = new Random();

		private DecimalFormat df = new DecimalFormat("#.######");

		private boolean currentlyVisibile = false;

		private GeoNamesAPI geoNamesAPI;

		private float displayMiddleX;

		private PhotoSearch photoSearch = new PhotoSearch();
		
		private Pair<Location, String> location;

	}

	public static final String TAG = FlickrService.class.getSimpleName();
	public static final String SHARED_PREFS_NAME = "flickrSettings";
}
