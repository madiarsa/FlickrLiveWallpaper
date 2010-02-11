package novoda.wallpaper;

import java.io.IOException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import novoda.wallpaper.flickr.Flickr;
import novoda.wallpaper.flickr.Photo;
import novoda.wallpaper.flickr.PhotoSearch;
import android.app.WallpaperManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.service.wallpaper.WallpaperService;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.WindowManager;

public class FlickrService extends WallpaperService {

	public static final String TAG = FlickrService.class.getSimpleName();

	@Override
	public Engine onCreateEngine() {
		return new FlickrEngine();
	}

	class FlickrEngine extends Engine {
		private Bitmap cachedBitmap = null;
		private int width;
		private int height;
		private Photo photo = null;

		@Override
		public void onCreate(SurfaceHolder surfaceHolder) {
			Display dm = ((WindowManager) getSystemService(WINDOW_SERVICE))
					.getDefaultDisplay();
			width = dm.getWidth();
			height = dm.getHeight();
			super.onCreate(surfaceHolder);
		}

		@Override
		public Bundle onCommand(String action, int x, int y, int z,
				Bundle extras, boolean resultRequested) {
			if (action.equals(WallpaperManager.COMMAND_TAP) && photo != null
					&& !photo.getUrl_o().equalsIgnoreCase("")) {
				final Intent intent = new Intent(Intent.ACTION_VIEW, Uri
						.parse(photo.getUrl_o()));
				intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(intent);
			}
			return super.onCommand(action, x, y, z, extras, resultRequested);
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			if (visible) {
				Thread t = new Thread() {
					public void run() {
						getPhoto();
						drawFrame();
					}
				};
				t.start();
			} else {
				//drawFrame();
			}
		}
		

		@Override
		public void onDestroy() {
			if (cachedBitmap != null)
				cachedBitmap.recycle();
			photo = null;
			cachedBitmap = null;
			super.onDestroy();
		}

		private void drawFrame() {
			final SurfaceHolder holder = getSurfaceHolder();
			Canvas c = null;
			try {
				c = holder.lockCanvas();
				if (c != null && cachedBitmap != null) {
					c.drawBitmap(cachedBitmap, 0, 0, new Paint());
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}

		private void getPhoto() {
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
				final GregorianCalendar calendar = new GregorianCalendar();
				final Flickr<Photo> f = new PhotoSearch();

				List<Photo> list = f.with("accuracy", "11").with("lat",
						"" + location.getLatitude()).with("lon",
						"" + location.getLongitude()).with("tags",
						getHumanizeDate(calendar.get(Calendar.HOUR_OF_DAY)))
						.with("sort", "interestingness-desc").with("media",
								"photos").with("extras", "url_o")
						.fetchStructuredDataList();

				if (list.size() < 1) {
					list = f.with("accuracy", "11").with("lat",
							"" + location.getLatitude()).with("lon",
							"" + location.getLongitude()).with("tags", "city")
							.with("sort", "interestingness-desc").with("media",
									"photos").with("extras", "url_o")
							.fetchStructuredDataList();
				}

				for (Photo p : list) {
					if (p.getUrl_o() != null
							&& (p.getHeight_o() > height && p.getWidth_o() > width)) {
						photo = p;
						break;
					}
				}

				if (cachedBitmap == null && photo != null) {
					Bitmap original;
					try {
						original = photo.getPhoto();
						if (original != null) {
							cachedBitmap = scale(original, width, height);
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
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
		Bitmap scale(Bitmap bitmap, int width, int height) {
			final int bitmapWidth = bitmap.getWidth();
			final int bitmapHeight = bitmap.getHeight();

			final float scale = Math.max((float) width / (float) bitmapWidth,
					(float) height / (float) bitmapHeight);

			final int scaledWidth = (int) (bitmapWidth * scale);
			final int scaledHeight = (int) (bitmapHeight * scale);
			return Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight,
					true);
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
	}
}
