package novoda.wallpaper;

import java.io.IOException;
import java.io.InputStream;
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
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
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
			if (cachedBitmap != null){
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

		private void refreshImage() {
			Location location = getRecentLocation();

			if (location == null) {
				// no location
			} else {
				// List<Photo> list = getPhotosFromExactLocation(location);
				List<Photo> photos = getPhotosFromSurrounding(location);
				PhotoSpec<String, Object> photoSpecs = getBestSpecs(photos);

				if (photoSpecs != null) {
					cachedBitmap = retrievePhotoFromSpecs(photoSpecs);
				}
			}
		}

		private Bitmap retrievePhotoFromSpecs(PhotoSpec<String, Object> photoSpecs) {
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
				scale = Math.min((float) width / (float) bitmapWidth, (float) height / (float) bitmapHeight);
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
			Location location =null;
			for (String provider : locManager.getProviders(true)) {
				location = locManager.getLastKnownLocation(provider);
				if (location != null){
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
			if(opts>1){
				opts=randomWheel.nextInt(opts-1);
				return options.get(opts);
			}
			
			return options.get(0);
		}

		/*
		 * Use location to establish a place name via the GeoName API
		 * Query Flickr to establish if photos are available
		 * Requery if photos can be divided into pages (to help randomness)
		 */
		private List<Photo> getPhotosFromSurrounding(Location location) {
			Log
					.d(TAG,
							"Requesting photo details based on approximate location");
			String place = geoNamesAPI.getNearestPlaceName(df.format(location
					.getLatitude()), df.format(location.getLongitude()));

			// Add random to ensure varying results
			photoSearch.with("accuracy", "11");
			photoSearch.with("tags", place);
			photoSearch.with("sort", "interestingness-desc");
			photoSearch.with("media", "photos");
			photoSearch.with("extras", "url_m");
			photoSearch.with("extras", "url_o");
			photoSearch.with("extras", "url_s");
			photoSearch.with("per_page", "50");
			List<Photo> list = photoSearch.fetchStructuredDataList();
			
			
			if(list.size()>1){
				int square = (int)Math.sqrt(list.size());
				photoSearch.with("per_page", ""+square);
				photoSearch.with("page", ""+ randomWheel.nextInt(square-1));
				list = photoSearch.fetchStructuredDataList();
			}
			
			return list;
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
			photoSearch.with("tags", getHumanizeDate(new GregorianCalendar()
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
		 * @param time
		 *            as in 22 for 10 o clock in the evening
		 * @return a human readable tag
		 */
		String getHumanizeDate(int time) {
			// between 22 and 5 it is the night
			if ((time > 22 && time <= 24) || (time >= 0 && time <= 5)) {
				return "night";
			}
			// between 5 and 9 it is dawn
			if (time > 5 && time <= 7) {
				return "dawn";
			}
			// between 9 and 12 it is morning
			if (time > 7 && time <= 11) {
				return "morning";
			}
			// between 12 and 15 noon
			if (time > 11 && time <= 15) {
				return "noon";
			}
			// between 15 and 19 it is afternoon
			if (time > 15 && time <= 19) {
				return "afternoon";
			}
			// between 19 and 22 it is the evening
			if (time > 19 && time <= 22) {
				return "evening";
			}
			// should not be here but just in case as it s getting late
			return "city";
		}

		private final Runnable mDrawWallpaper = new Runnable() {
			public void run() {
				if (currentlyVisibile) {
					refreshImage();
					drawCanvas();
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

		private PhotoSearch photoSearch = new PhotoSearch();

	}

	public static final String TAG = FlickrService.class.getSimpleName();
	public static final String SHARED_PREFS_NAME = "flickrSettings";
}
