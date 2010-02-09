package novoda.wallpaper;

import java.io.IOException;

import novoda.wallpaper.flickr.Flickr;
import novoda.wallpaper.flickr.Photo;
import novoda.wallpaper.flickr.PhotoSearch;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class FlickrService extends WallpaperService {

	public static final String TAG = FlickrService.class.getSimpleName();

	@Override
	public Engine onCreateEngine() {
		return new FlickrEngine();
	}

	class FlickrEngine extends Engine {

		private float mOffset = 0;

		@Override
		public void onOffsetsChanged(float xOffset, float yOffset, float xStep,
				float yStep, int xPixels, int yPixels) {
			mOffset = xOffset;
			drawFrame();
		}

		@Override
		public void onVisibilityChanged(boolean visible) {
			super.onVisibilityChanged(visible);
			SurfaceHolder holder = getSurfaceHolder();
			Flickr<Photo> f = new PhotoSearch();
			// accuracy=11&lat=38.898556&lon=-77.037852
			Photo photo = f.with("accuracy", "11").with("lat", "38.898556")
					.with("lon", "-77.037852").fetchStructuredDataList().get(0);
			try {
				Canvas can = holder.lockCanvas();
				if (can != null) {
					can.drawBitmap(photo.getPhoto(), 0	, 0, new Paint());
					holder.unlockCanvasAndPost(can);
				}
				
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		private void drawFrame() {
			final SurfaceHolder holder = getSurfaceHolder();

			Canvas c = null;
			try {
				c = holder.lockCanvas();
				if (c != null) {
					// draw something
				}
			} finally {
				if (c != null)
					holder.unlockCanvasAndPost(c);
			}
		}
	}
}
