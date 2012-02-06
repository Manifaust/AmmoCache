/*
 * Copyright (C) 2011 Tony Wong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.bitfable.ammocache.download;

import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

/**
 * Extends this class to create an image downloader. Images will automatically
 * be cached in-memory. The in-memory cache size item count limit can be
 * configured by changing {@link #HARD_CACHE_CAPACITY}. In-memory LRU cache can
 * be set to auto-purge itself to save meory. This is configured with
 * {@link #DELAY_BEFORE_PURGE}.
 * 
 * See {@link UrlImageDownloader} for an example implementation
 * 
 * The {@link AsyncTask} workflow and in-memory cache is based on code from
 * Gilles Debunne:
 * 
 * http://android-developers.blogspot.com/2010/07/multithreading-for-performance.html
 * http://code.google.com/p/android-imagedownloader/
 * 
 */
abstract public class AbstractImageDownloader {
	/**
	 * Minimum amount of time between updates for the {@link ProgressListener}
	 */
	private static final int PUBLISH_PROGRESS_TIME_THRESHOLD_MILLI = 500;
	
	/**
	 * Max number of items allowed inside in-memory LRU cache
	 */
    private static final int HARD_CACHE_CAPACITY = 64;
    
    /**
     * Amount of time of inactivity to wait before purging in-memory cache, set
     * to -1 for no auto-purging
     */
    private static final int DELAY_BEFORE_PURGE = -1; // 10 * 1000; // in milliseconds

	private static final String TAG = "AbstractImageDownloader";

	public static final String KEY_PROGRESS = "KEY_PROGRESS";
	public static final String KEY_ELAPSED_TIME = "KEY_ELAPSED_TIME";
	private Handler mHandler;

	@SuppressWarnings("unused")
	private AbstractImageDownloader() { }
	
	protected AbstractImageDownloader(Context context) {
		mHandler = new Handler(context.getMainLooper());
	}
	
	public void download(String key, ImageView imageView) {
		download(key, imageView, null, null);
	}
	
	public void download(String key, ImageView imageView, Bitmap defaultBitmap) {
		download(key, imageView, defaultBitmap, null);
	}
	
	public void download(String key, ImageView imageView, ProgressListener progressListener) {
		download(key, imageView, null, progressListener);
	}
	
	public void download(String key, ImageView imageView, Bitmap defaultBitmap, ProgressListener progressListener) {
        resetPurgeTimer();
        Bitmap bitmap = getBitmapFromCache(key);

        if (bitmap == null) {
            forceDownload(key, imageView, defaultBitmap, progressListener);
        } else {
            cancelPotentialDownload(key, imageView);
            imageView.setImageBitmap(bitmap);
            if (progressListener != null) progressListener.onProgressUpdated(100, 0L);
        }
	}	

    /**
     * Same as download but the image is always downloaded and the cache is not used.
     * Kept private at the moment as its interest is not clear.
     */
    private void forceDownload(String key, ImageView imageView, Bitmap defaultBitmap, ProgressListener progressListener) {
        // State sanity: key is guaranteed to never be null in DownloadedDrawable and cache keys.
    	if (key == null) {
            imageView.setImageDrawable(null);
            return;
        }

        if (cancelPotentialDownload(key, imageView)) {
	         ImageDownloadTask task = new ImageDownloadTask(key, imageView, progressListener);
	        
	         // Default cyan background drawable
	         Drawable downloadedDrawable;
	         if (defaultBitmap == null) {
	        	 downloadedDrawable = new DownloadedDrawable(task);
	         } else {
	        	 downloadedDrawable = new DefaultImageDrawable(task, defaultBitmap);
	         }
	         
	         imageView.setImageDrawable(downloadedDrawable);
	         task.execute();
        }
    }

    private static boolean cancelPotentialDownload(String key, ImageView imageView) {
		ImageDownloadTask downloadTask = getDownloadTask(imageView);
		
	    if (downloadTask != null) {
	        if (downloadTask.mKey == null || !downloadTask.mKey.equals(key)) {
	            downloadTask.cancel(true);
	            downloadTask.setProgressListener(null);
	        } else {
	            // The same image is already being downloaded.
	            return false;
	        }
	    }
	    
	    return true;
	}
	
	private static ImageDownloadTask getDownloadTask(ImageView imageView) {		
		if (imageView != null) {
	        Drawable drawable = imageView.getDrawable();
	        
	        if (drawable instanceof DownloadedDrawable) {
	            DownloadedDrawable downloadedDrawable = (DownloadedDrawable) drawable;
	            return downloadedDrawable.getDownloadTask();
	        } else if (drawable instanceof DefaultImageDrawable) {
	        	DefaultImageDrawable defaultImageDrawable = (DefaultImageDrawable) drawable;
	        	return defaultImageDrawable.getDownloadTask();
	        }
	    }
		
		return null;
	}
	
    private final class ImageDownloadTask extends AsyncTask<Void, Void, Bitmap> {
		String mKey;
    	private WeakReference<ImageView> mImageViewReference;
		private ProgressListener mProgressListener;
		private long mTimeBegin;
		private long mLastUpdateTime;

		public ImageDownloadTask(String key, ImageView imageView, ProgressListener progressListener) {
    		mKey = key;
    		mImageViewReference = new WeakReference<ImageView>(imageView);
    		mProgressListener = progressListener;
    		mTimeBegin = SystemClock.elapsedRealtime();
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			return downloadImage();
		}
		
		public void publishProgress(final int progress) {
			if (mProgressListener == null) return;
			
			if (progress < 100 && SystemClock.elapsedRealtime() - mLastUpdateTime < PUBLISH_PROGRESS_TIME_THRESHOLD_MILLI) {
				return;
			} else {
				mLastUpdateTime = SystemClock.elapsedRealtime();
			}
			
			mHandler.post(new Runnable() {
				@Override
				public void run() {
					if (mProgressListener == null) {
						return;
					}
					
					long elapsedTime = mLastUpdateTime - mTimeBegin;
					mProgressListener.onProgressUpdated(progress, elapsedTime);
				}
			});
		}
		
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled()) bitmap = null;
			
			addBitmapToCache(mKey, bitmap);
			
			if (bitmap != null) {
			    ImageView imageView = mImageViewReference.get();
			    ImageDownloadTask bitmapDownloaderTask = getDownloadTask(imageView);
			    // Change bitmap only if this process is still associated with it
			    if (this == bitmapDownloaderTask) {
			        imageView.setImageBitmap(bitmap);
			    }
			} else {
				Log.w(TAG, "could not download bitmap: " + mKey);
			}
		}
		
	    private Bitmap downloadImage() {
	    	return download(mKey, mImageViewReference);
	    }
	    
		public void setProgressListener(ProgressListener progressListener) {
			mProgressListener = progressListener;
		}
    }
    
    abstract protected Bitmap download(String key, WeakReference<ImageView> imageViewRef);
    
    protected boolean isCancelled(WeakReference<ImageView> imageViewRef) {
    	ImageView imageView = imageViewRef.get();
    	
    	if (imageView == null) return true;
    	
    	ImageDownloadTask task = getDownloadTask(imageView);
    	
    	if (task == null) return true;
    	
    	return task.isCancelled();
    }
    
    protected void publishProgress(int progress, WeakReference<ImageView> imageViewRef) {
    	ImageView imageView = imageViewRef.get();
    	
    	if (imageView == null) return;
    	
    	ImageDownloadTask task = getDownloadTask(imageView);
    	
    	if (task == null) return;
    	
    	task.publishProgress(progress);
    }
    
    private static final class DownloadedDrawable extends ColorDrawable {
        private final WeakReference<ImageDownloadTask> downloadTaskReference;

        public DownloadedDrawable(ImageDownloadTask downloadTask) {
            super(Color.TRANSPARENT);
            downloadTaskReference = new WeakReference<ImageDownloadTask>(downloadTask);
        }

        public ImageDownloadTask getDownloadTask() {
            return downloadTaskReference.get();
        }
    }
    
    private static final class DefaultImageDrawable extends BitmapDrawable {
        private WeakReference<ImageDownloadTask> downloadTaskReference = null;

        public DefaultImageDrawable(ImageDownloadTask downloadTask, Bitmap bitmap) {
        	super(bitmap);
        	downloadTaskReference = new WeakReference<ImageDownloadTask>(downloadTask);
		}
        
        public ImageDownloadTask getDownloadTask() {
            return downloadTaskReference.get();
    	}
    }
    
    public static interface ProgressListener {
    	/**
    	 * Progress callback for the download so far. This will be called on the
    	 * main thread so it must return quickly.
    	 * @param progressPercentage the progress of the download so far, could
    	 * be greater than 100 if content-length is reported incorrectly. If
    	 * content length is missing or 0, this progress will always read -1
    	 * @param timeElapsedMilli the time used so far for downloading
    	 */
    	void onProgressUpdated(int progressPercentage, long timeElapsedMilli);
    }
    

    /*
     * Cache-related fields and methods.
     * 
     * We use a hard and a soft cache. A soft reference cache is too aggressively cleared by the
     * Garbage Collector.
     */
    
    // Hard cache, with a fixed maximum capacity and a life duration
    private HashMap<String, Bitmap> sHardBitmapCache =
        new LinkedHashMap<String, Bitmap>(HARD_CACHE_CAPACITY / 2, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(LinkedHashMap.Entry<String, Bitmap> eldest) {
            if (size() > HARD_CACHE_CAPACITY) {
                // Entries push-out of hard reference cache are transferred to soft reference cache
                sSoftBitmapCache.put(eldest.getKey(), new SoftReference<Bitmap>(eldest.getValue()));
                return true;
            } else
                return false;
        }
    };

    // Soft cache for bitmaps kicked out of hard cache
    private ConcurrentHashMap<String, SoftReference<Bitmap>> sSoftBitmapCache =
        new ConcurrentHashMap<String, SoftReference<Bitmap>>(HARD_CACHE_CAPACITY / 2);

    private final Handler purgeHandler = new Handler();

    private final Runnable purger = new Runnable() {
        public void run() {
            clearCache();
        }
    };

    /**
     * Adds this bitmap to the cache.
     * @param bitmap The newly downloaded bitmap.
     */
    private void addBitmapToCache(String key, Bitmap bitmap) {
        if (bitmap != null) {
            synchronized (sHardBitmapCache) {
                sHardBitmapCache.put(key, bitmap);
            }
        }
    }

    /**
     * @param key The key of the image that will be retrieved from the cache.
     * @return The cached bitmap or null if it was not found.
     */
    private Bitmap getBitmapFromCache(String key) {
        // First try the hard reference cache
        synchronized (sHardBitmapCache) {
            final Bitmap bitmap = sHardBitmapCache.get(key);
            if (bitmap != null) {
                // Bitmap found in hard cache
                // Move element to first position, so that it is removed last
                sHardBitmapCache.remove(key);
                sHardBitmapCache.put(key, bitmap);
                return bitmap;
            }
        }

        // Then try the soft reference cache
        SoftReference<Bitmap> bitmapReference = sSoftBitmapCache.get(key);
        if (bitmapReference != null) {
            final Bitmap bitmap = bitmapReference.get();
            if (bitmap != null) {
                // Bitmap found in soft cache
                return bitmap;
            } else {
                // Soft reference has been Garbage Collected
                sSoftBitmapCache.remove(key);
            }
        }

        return null;
    }
 
    /**
     * Clears the image cache used internally to improve performance. Note that for memory
     * efficiency reasons, the cache will automatically be cleared after a certain inactivity delay.
     */
    protected void clearCache() {
        sHardBitmapCache.clear();
        sSoftBitmapCache.clear();
    }

    /**
     * Allow a new delay before the automatic cache clear is done.
     */
    private void resetPurgeTimer() {
    	if (DELAY_BEFORE_PURGE < 0) return;
    	
        purgeHandler.removeCallbacks(purger);
        purgeHandler.postDelayed(purger, DELAY_BEFORE_PURGE);
    }

}
