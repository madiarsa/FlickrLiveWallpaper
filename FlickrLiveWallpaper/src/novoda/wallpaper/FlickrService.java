package novoda.wallpaper;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Random;

import novoda.wallpaper.flickr.Flickr;
import novoda.wallpaper.flickr.Photo;
import novoda.wallpaper.flickr.PhotoSearch;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
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

	public static final String TAG = FlickrService.class.getSimpleName();
	public static final String SHARED_PREFS_NAME = "flickrSettings";
	
	@Override
	public Engine onCreateEngine() {
		return new FlickrEngine();
	}

	class FlickrEngine extends Engine implements SharedPreferences.OnSharedPreferenceChangeListener{
		
		private final Handler mHandler = new Handler();
		
        private final Runnable mDrawWallpaper = new Runnable() {
        	public void run() {
                if (currentlyVisibile) {
                	getPhoto();
                	drawFrame();
                }else{
                	//Waiting until wallpaper becomes visible
                	mHandler.postDelayed(mDrawWallpaper, 2000);
                }
        	}
        };

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

        
		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			super.onCreate(surfaceHolder);
			Display dm = ((WindowManager) getSystemService(WINDOW_SERVICE))
					.getDefaultDisplay();
			displayWidth = dm.getWidth();
			displayHeight = dm.getHeight();
			
            mPrefs = FlickrService.this.getSharedPreferences(SHARED_PREFS_NAME, 0);
            mPrefs.registerOnSharedPreferenceChangeListener(this);
            onSharedPreferenceChanged(mPrefs, null);
		}

		@Override
		public Bundle onCommand(String action, int x, int y, int z,
				Bundle extras, boolean resultRequested) {
			if (action.equals(WallpaperManager.COMMAND_TAP) && photo != null && !photo.getUrl_o().equalsIgnoreCase("")) {
				final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(photo.getUrl_o()));
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
				if(reSynchNeeded){
					mHandler.post(mDrawWallpaper);
					lastSync = System.currentTimeMillis();
				}
			} else {
                mHandler.removeCallbacks(mDrawWallpaper);
			}
		}

		@Override
		public void onDestroy() {
			if (cachedBitmap != null)
				cachedBitmap.recycle();
			photo = null;
			cachedBitmap = null;
            mHandler.removeCallbacks(mDrawWallpaper);
			super.onDestroy();
		}

		private void drawFrame() {
			Log.d(TAG, "Drawing Image");
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				if (c != null && cachedBitmap != null) {
					c.drawBitmap(cachedBitmap, 0, cachedTopMargin , new Paint());
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		private void getPhoto() {
			Log.d(TAG, "Retrieving Image");
			
			final LocationManager locManager = (LocationManager) FlickrService.this
					.getBaseContext()
					.getSystemService(Context.LOCATION_SERVICE);

			Location location = null;
			for (String provider : locManager.getProviders(true)) {
				location = locManager.getLastKnownLocation(provider);
				if (location != null)
					break;
			}

			if (location == null) {
				// no location
			} else {
				final Flickr<Photo> f = new PhotoSearch();

				double latitude = location.getLatitude();
				double longitude = location.getLongitude();
				Log.i(TAG, "Longitude=["+longitude+"] latitude=["+latitude+"]");
				
				//Random no. between 0.1 > 0.0099
				double d = randomWheel.nextDouble();
				d = Double.valueOf(df.format((d * (0.1 - 0.0099))));
				Log.i(TAG, "latitude =" + (df.format( latitude + d)));
				
				//Add random to ensure varying results
				f.with("lat","" + df.format( latitude + d));
				f.with("lon", "" + df.format( longitude + d));
				f.with("accuracy", "16");
				f.with("tags",	getHumanizeDate(new GregorianCalendar().get(Calendar.HOUR_OF_DAY)));
				f.with("sort", "interestingness-desc");
				f.with("media", "photos");
				f.with("extras", "url_o");
				f.with("per_page", "1");
				f.with("page", ""+ randomWheel.nextInt(5));
				List<Photo> list = f.fetchStructuredDataList();

				if (list.size() < 1) {
					f.remove("accuracy", "16");
					f.with("accuracy", "11");
				}

				for (Photo p : list) {
					if (p.getUrl_o() != null) {
						photo = p;
						break;
					}
				}

				if (photo != null) {
					cachedBitmap = refreshCachedImage();
				}
			}
		}

		private Bitmap refreshCachedImage() {
			Bitmap original=null;
			try {
				original = photo.getPhoto();
			} catch (IOException e) {
				e.printStackTrace();
			}
			
			if (original != null) {
				return scaleImage(original, displayWidth, displayHeight);
			}else{
				return null;
			}
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
			
			if(alignImgInMiddle ){
				scale = Math.min((float) width / (float) bitmapWidth, (float) height / (float) bitmapHeight);
			}else{
				scale = Math.max((float) width / (float) bitmapWidth, (float) height / (float) bitmapHeight);
			}

			final int scaledWidth = (int) (bitmapWidth * scale);
			final int scaledHeight = (int) (bitmapHeight * scale);
			
			Log.d(TAG, "Scaling Bitmap (height x width): Orginal[" + bitmapHeight +"x" + bitmapWidth +"], New[" + scaledHeight +"x"+scaledWidth + "]");
			Bitmap createScaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight,	true);
			
			/*
			 * Work out the Top Margin to align the image in the middle of the screen
			 * screenDivisions = totalScreenHeight/BitmapHeight
			 * cachedTopMargin = (screenDivisions * 0.5) + (BitmapHeight*0.5)
			 * 
			 */
			if(alignImgInMiddle){
				cachedTopMargin = Math.round(((float)(Math.min((float)displayHeight, (float)scaledHeight)) *0.5)+ ((float)scaledHeight*0.5)); 
			}else{
				cachedTopMargin = 0;
			}
			
			return createScaledBitmap;
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

		public void onSharedPreferenceChanged(
				SharedPreferences sharedPreferences, String key) {
			String scaleSetting = sharedPreferences.getString(PREF_SCALE_TYPE, PREF_SCALE_TYPE_MIDDLE);
			
			boolean beforePrefCalled=alignImgInMiddle;
			
			
			if(scaleSetting.equals(PREF_SCALE_TYPE_FULL)){
				alignImgInMiddle = false;
			}else{
				alignImgInMiddle = true;
			}
			
			if(!(alignImgInMiddle == beforePrefCalled)){
				Log.i(TAG, "pref changed");
				mHandler.post(mDrawWallpaper);
			}
			
		}
		
	}
}
