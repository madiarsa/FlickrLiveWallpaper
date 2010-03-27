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
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.PorterDuff.Mode;
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

/*
 * ===================================
 * Flickr Live Wallpaper 
 * http://github.com/novoda/flickrlivewallpaper 
 * ===================================
 *  
 * Retrieves and displays a photo from Flickr based on your current location.
 * The majority of locations in the world do not have photos specifically 
 * geoTagged on Flickr and so instead a query using the users exact location 
 * is sent to GeoNames establish a good approximation and then queries 
 * Flickr using the place name as a tag.
 * 
 * This code was developed by Novoda (http://www.novoda.com)
 * You are welcome to use this code in however you see fit.
 *
 */
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

			txtPaint = new Paint();
			txtPaint.setAntiAlias(true);
			txtPaint.setColor(Color.WHITE);
			txtPaint.setTextSize(37);
			txtPaint.setStyle(Paint.Style.STROKE);
			Typeface typeFace = Typeface.createFromAsset(getBaseContext()
					.getAssets(), "fonts/ArnoProRegular10pt.otf");
			txtPaint.setTypeface(typeFace);
			txtPaint.setTextAlign(Paint.Align.CENTER);

			final Bitmap bg = BitmapFactory.decodeResource(getResources(),
					getResources().getIdentifier("bg_wallpaper_pattern",
							"drawable", "novoda.wallpaper"));

			BitmapShader mShader1 = new BitmapShader(bg,
					Shader.TileMode.REPEAT, Shader.TileMode.REPEAT);
			bg.recycle();
			bgPaint = new Paint();
			bgPaint.setShader(mShader1);

			frame = BitmapFactory.decodeResource(getResources(),
					getResources().getIdentifier("frame", "drawable",
							"novoda.wallpaper"));
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

		/*
		 * A new Wallpaper is requested every time the dashboard becomes visible
		 * within a reasonable time period to save queries being made overly
		 * often to save battery and bandwith.
		 * 
		 * @see
		 * android.service.wallpaper.WallpaperService.Engine#onVisibilityChanged
		 * (boolean)
		 */
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

		private void requestAndCacheImage(Location location, String placeName)
				throws IllegalStateException {
			if (cachedBitmap != null) {
				cachedBitmap.recycle();
			}
			// List<Photo> list = getPhotosFromExactLocation(location);
			List<Photo> photos = getPhotosFromApproxLocation(placeName,
					location);
			PhotoSpec<String, Object> photoSpecs = getBestSpecs(photos);

			if (photoSpecs != null) {
				cachedBitmap = retrievePhotoFromSpecs(photoSpecs);
			}
		}

		/*
		 * Using the GeoNames API establish an approximate location
		 * 
		 * @return Pair<Location, String>(Location, placeName)
		 */
		private Pair<Location, String> obtainLocation() throws ConnectException {
			Log
					.d(TAG,
							"Requesting photo details based on approximate location");
			final Location location = getRecentLocation();
			return new Pair<Location, String>(location, geoNamesAPI
					.getNearestPlaceName(df.format(location.getLatitude()), df
							.format(location.getLongitude())));
		}

		/*
		 * Using existing details of a photos specifications obtained from the
		 * Flickr API, request the binary stream from a HTTP connection
		 */
		private Bitmap retrievePhotoFromSpecs(
				PhotoSpec<String, Object> photoSpecs)
				throws IllegalStateException {
			Bitmap original = null;
			URL photoUrl = null;

			try {
				Log.d(TAG, "Requesting static image from Flickr=["
						+ (String) photoSpecs.get(PhotoSpec.PHOTOSPEC_URL)
						+ "]");
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

			if (original == null) {
				Log
						.e(TAG,
								"I'm not sure what went wrong but image coul not be retrieved");
				throw new IllegalStateException(
						"Whoops! We had problems retrieving an image. Please try again.");
			} else {
				original = scaleImage(original, displayWidth, displayHeight);
			}

			return original;
		}

		/*
		 * Renders a retrieved Photo to the Display
		 */
		private void drawCachedImage() {
			Log.d(TAG, "Drawing Canvas");
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				if (c != null && cachedBitmap != null) {
					c.drawPaint(bgPaint);
					c.drawBitmap(frame, 45, 180, new Paint());
					c.drawBitmap(cachedBitmap, FRAMED_IMG_LEFT_MARGIN, FRAMED_IMG_TOP_MARGIN, txtPaint);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		/**
		 * Scale images to fit the height/width of the drawing canvas.
		 * 
		 * @param bitmap
		 * @param width
		 * @param height
		 * @return
		 */
		private Bitmap scaleImage(Bitmap bitmap, int width, int height) {
			final int bitmapWidth = bitmap.getWidth();
			final int bitmapHeight = bitmap.getHeight();
			final float scale;

//			if (alignImgInMiddle) {
//				scale = Math.min((float) width / (float) bitmapWidth,
//						(float) height / (float) bitmapHeight);
//			} else {
//				scale = Math.max((float) width / (float) bitmapWidth,
//						(float) height / (float) bitmapHeight);
//			}
//
//			final int scaledWidth = (int) (bitmapWidth * scale);
//			final int scaledHeight = (int) (bitmapHeight * scale);
			
			final int scaledWidth = 318;
			final int scaledHeight = 251;

			
			/*
			 * Work out the Top Margin to align the image in the middle of the
			 * screen with a slightly larger bottom gutter for framing
			 * screenDivisions = totalScreenHeight/BitmapHeight cachedTopMargin
			 * = screenDivisions - (BitmapHeight*0.5)
			 */
			if (alignImgInMiddle) {
				final float screenDividedByPic = Math.min(
						(float) displayHeight, (float) scaledHeight);
				cachedImgTopMargin = Math
						.round((screenDividedByPic - (float) scaledHeight * 0.5));
			} else {
				cachedImgTopMargin = 0;
			}

			Log.d(TAG, "Scaling Bitmap (height x width): Orginal["
					+ bitmapHeight + "x" + bitmapWidth + "], New["
					+ scaledHeight + "x" + scaledWidth + "]");

			return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight,
					true);
		}

		/*
		 * Initial loading feedback Also clears the screen of any old artifacts
		 */
		private void drawInitialNotification() {
			Log.d(TAG, "Displaying loading info");
			final float x = displayMiddleX;
			final float y = 180;
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				c.drawPaint(bgPaint);
				final Bitmap decodeResource = BitmapFactory.decodeResource(
						getResources(), getResources().getIdentifier(
								"ic_logo_flickr", "drawable",
								"novoda.wallpaper"));
				if (c != null) {
					c
							.drawBitmap(decodeResource, (x - decodeResource
									.getWidth() * 0.5f), y, txtPaint);
					c.drawText("Downloading Image", x, y + 108, txtPaint);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		/*
		 * Draw Background
		 */
		private void drawBG() {
			Log.d(TAG, "Displaying loading info");
			final float x = displayMiddleX;
			final float y = 180;
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();

				if (c != null) {
					c.drawPaint(bgPaint);

				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		/*
		 * Loading feedback to assure the user a place has been correctly
		 * retrieved. This feedback is intended to help alleviate some of the
		 * lag in retrieving and then resizing the image but also informs the
		 * user of their presumed location.
		 */
		private void drawDetailedLoadingNotification(String placeName) {
			Log.d(TAG, "Displaying loading details for placename=[" + placeName
					+ "]");
			final float x = displayMiddleX;
			final float y = 180;
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				c.drawPaint(bgPaint);
				final Bitmap decodeResource = BitmapFactory.decodeResource(
						getResources(), getResources().getIdentifier(
								"ic_logo_flickr", "drawable",
								"novoda.wallpaper"));
				if (c != null) {
					c
							.drawBitmap(decodeResource, (x - decodeResource
									.getWidth() * 0.5f), y, txtPaint);
					c.drawText("Downloading Image", x, y + 108, txtPaint);
					drawTextInRect(c, txtPaint, new Rect((int) x,
							(int) y + 200, 700, 300),
							"Looking for images around " + placeName);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		/*
		 * Provides error feedback for users Also clears the screen of any old
		 * artifacts
		 */
		private void drawErrorNotification(String error) {
			Log.e(TAG, error);
			float x = displayMiddleX;
			float y = 180;
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				c.drawPaint(bgPaint);
				final Bitmap decodeResource = BitmapFactory.decodeResource(
						getResources(), getResources().getIdentifier(
								"ic_smile_sad_48", "drawable",
								"novoda.wallpaper"));
				if (c != null) {

					c
							.drawBitmap(decodeResource, (x - decodeResource
									.getWidth() * 0.5f), y, txtPaint);
					drawTextInRect(c, txtPaint, new Rect((int) x,
							(int) y + 108, 700, 300), error);
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		/*
		 * TODO: Possibility of better ways to wrap text using staticLayout
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
				final int charactersRemaining = end - start + 1;
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
		 * Chosen an image from within a list of suitable photo specs.
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

			final int opts = options.size();
			if (opts > 1) {
				return options.get(randomWheel.nextInt(opts - 1));
			}

			return options.get(0);
		}

		/*
		 * Establish current place name via the GeoName API Query Use place name
		 * to establish if photos are available as a tag on Flickr Requery if
		 * photos can be divided into pages (to help randomness of results)
		 */
		private List<Photo> getPhotosFromApproxLocation(String placeNameTag,
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

		/*
		 * Return Flickr photos based on the exact user's location
		 */
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
		private String getPeriodOfDay(int time) {
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
		 * Main thread of re-execution. Once called, an image will be retrieved
		 * and then then drawn. This thread will wait until the canvas is
		 * visible for when a a dialog or preference screen is shown.
		 */
		private final Runnable mDrawWallpaper = new Runnable() {
			public void run() {
				if (currentlyVisibile) {
					drawInitialNotification();

					try {
						location = obtainLocation();
					} catch (ConnectException e) {
						location = null;
						drawErrorNotification("Could not connect to the internet to find your location");
					}

					if (location != null) {
						drawDetailedLoadingNotification(location.second);

						try {
							requestAndCacheImage(location.first,
									location.second);
							drawCachedImage();
						} catch (IllegalStateException e) {
							Log.e(TAG, e.getMessage());
							drawErrorNotification(e.getMessage());
						}

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

		private static final float FRAMED_IMG_TOP_MARGIN = 220;

		private static final int FRAMED_IMG_LEFT_MARGIN = 86;
		
		private long cachedImgTopMargin = 0;
		
		private boolean alignImgInMiddle = true;

		private SharedPreferences mPrefs;

		private final Random randomWheel = new Random();

		private DecimalFormat df = new DecimalFormat("#.######");

		private boolean currentlyVisibile = false;

		private Paint txtPaint;

		private GeoNamesAPI geoNamesAPI;

		private float displayMiddleX;

		private final PhotoSearch photoSearch = new PhotoSearch();

		private Pair<Location, String> location;

		private Paint bgPaint;

		private Bitmap frame;

	}

	public static final String TAG = FlickrService.class.getSimpleName();
	public static final String SHARED_PREFS_NAME = "flickrSettings";
}
