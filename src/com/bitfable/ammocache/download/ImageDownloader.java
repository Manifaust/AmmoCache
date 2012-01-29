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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.http.util.ByteArrayBuffer;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.ImageView;

import com.bitfable.ammocache.io.FlushedInputStream;

/**
 * Use this class to download images and load them onto ImageView instances.
 * You can configure the HTTP cache size used by modifying {@link #CACHE_SIZE}
 * 
 * Many of the optimizations in this code came from an Android Developer Blog
 * article by Jesse Wilson:
 * 
 * http://android-developers.blogspot.com/2011/09/androids-http-clients.html
 * 
 */
public class ImageDownloader {
	private static final int BYTE_ARRAY_BUFFER_INCREMENTAL_SIZE = 1048;
	private static final String TAG = "ImageDownloader";
	private static final long CACHE_SIZE = 10 * 1024 * 1024; // 10 MiB
	private static final String CACHE_FILE_NAME = "image_downloader_cache";
	
	@SuppressWarnings("unused")
	private ImageDownloader() { }
	
	public ImageDownloader(Context context) {
		disableConnectionReuseIfNecessary();
		
		if (context != null) {
			enableHttpResponseCache(context);
		}
	}
	
	/**
	 * Prior to Froyo, HttpURLConnection had some frustrating bugs. In
	 * particular, calling close() on a readable InputStream could poison the
	 * connection pool. Work around this by disabling connection pooling.
	 */
	private void disableConnectionReuseIfNecessary() {
	    // HTTP connection reuse which was buggy pre-froyo
	    if (Integer.parseInt(Build.VERSION.SDK) < Build.VERSION_CODES.FROYO) {
	        System.setProperty("http.keepAlive", "false");
	    }
	}
	
	/**
	 * Use reflection to enable HTTP response caching on devices that support
	 * it. This sample code will turn on the response cache on Ice Cream
	 * Sandwich without affecting earlier releases.
	 */
	private void enableHttpResponseCache(Context context) {
		File cacheDir = context.getCacheDir();
		if (cacheDir == null) {
			Log.w(TAG, "cache directory could not be found");
			return;
		}
		
	    try {
	        long httpCacheSize = CACHE_SIZE;
	        File httpCacheDir = new File(cacheDir, CACHE_FILE_NAME);
	        Class.forName("android.net.http.HttpResponseCache")
	            .getMethod("install", File.class, long.class)
	            .invoke(null, httpCacheDir, httpCacheSize);
	    } catch (Exception httpResponseCacheNotAvailable) {
	    	Log.v(TAG, "HttpResponseCache is not available");
	    }
	}
	
	public void download(String imageUrl, ImageView imageView) {
		download(imageUrl, imageView, null);
	}
	
	public void download(String imageUrl, ImageView imageView, ProgressListener progressListener) {
	     if (cancelPotentialDownload(imageUrl, imageView)) {
	         ImageDownloadTask task = new ImageDownloadTask(imageUrl, imageView, progressListener);
	         DownloadedDrawable downloadedDrawable = new DownloadedDrawable(task);
	         imageView.setImageDrawable(downloadedDrawable);
	         task.execute();
	     }
	}
	
	private static boolean cancelPotentialDownload(String url, ImageView imageView) {
		ImageDownloadTask downloadTask = getDownloadTask(imageView);

	    if (downloadTask != null) {
	        String bitmapUrl = downloadTask.url;
	        if ((bitmapUrl == null) || (!bitmapUrl.equals(url))) {
	            downloadTask.cancel(true);
	        } else {
	            // The same URL is already being downloaded.
	            return false;
	        }
	    }
	    return true;
	}
	
	private static ImageDownloadTask getDownloadTask(ImageView imageView) {
	    if (imageView != null) {
	        Drawable drawable = imageView.getDrawable();
	        if (drawable instanceof DownloadedDrawable) {
	            DownloadedDrawable downloadedDrawable = (DownloadedDrawable)drawable;
	            return downloadedDrawable.getDownloadTask();
	        }
	    }
	    return null;
	}
	
    private static final class ImageDownloadTask extends AsyncTask<Void, Void, Bitmap> {
    	private static final int PUBLISH_PROGRESS_TIME_THRESHOLD_MILLI = 500;
		String url;
    	private WeakReference<ImageView> mImageViewReference;
    	private int mBytesDownloaded;
		private int mContentLength;
		private ProgressListener mProgressListener;
		private long mTimeBegin;

		public ImageDownloadTask(String imageUrl, ImageView imageView, ProgressListener progressListener) {
    		url = imageUrl;
    		mImageViewReference = new WeakReference<ImageView>(imageView);
    		mProgressListener = progressListener;
    		mTimeBegin = SystemClock.elapsedRealtime();
		}

		@Override
		protected Bitmap doInBackground(Void... params) {
			return downloadImage(url);
		}
		
		@Override
		protected void onProgressUpdate(Void... values) {
			if (mContentLength <= 0 || mProgressListener == null) return;
			
			long timeNow = SystemClock.elapsedRealtime();
			mProgressListener.onProgressUpdated(mBytesDownloaded * 100 / mContentLength, timeNow - mTimeBegin);
		}
    	
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (isCancelled()) bitmap = null;
			
			if (bitmap != null) {
			    ImageView imageView = mImageViewReference.get();
			    ImageDownloadTask bitmapDownloaderTask = getDownloadTask(imageView);
			    // Change bitmap only if this process is still associated with it
			    if (this == bitmapDownloaderTask) {
			        imageView.setImageBitmap(bitmap);
			    }
			} else {
				Log.w(TAG, "could not download bitmap: " + url);
			}
		}
		
	    private Bitmap downloadImage(String imageUrl) {
	    	URL url;
	    	
			try {
				url = new URL(imageUrl);
			} catch (MalformedURLException e) {
				Log.e(TAG, "url is malformed: " + imageUrl, e);
				return null;
			}
			
	    	HttpURLConnection urlConnection;
			try {
				urlConnection = (HttpURLConnection) url.openConnection();
			} catch (IOException e) {
				Log.e(TAG, "error while opening connection", e);
				return null;
			}
			
	    	Bitmap bitmap = null;
	    	InputStream httpStream = null;
	    	try {
	    		mContentLength = urlConnection.getContentLength();
	    		httpStream = new FlushedInputStream(urlConnection.getInputStream());
	    		ByteArrayBuffer baf = new ByteArrayBuffer(BYTE_ARRAY_BUFFER_INCREMENTAL_SIZE);
	    		byte[] buffer = new byte[BYTE_ARRAY_BUFFER_INCREMENTAL_SIZE];
	    		while (!isCancelled()) {
	    			int incrementalRead = httpStream.read(buffer);
	    			if (incrementalRead == -1) {
	    				break;
	    			}
	    			mBytesDownloaded += incrementalRead;
	    			if (SystemClock.elapsedRealtime() - mTimeBegin > PUBLISH_PROGRESS_TIME_THRESHOLD_MILLI
	    					|| mBytesDownloaded == mContentLength) {
	    				publishProgress();
	    			}
	    			baf.append(buffer, 0, incrementalRead);
	    		}

	    		if (isCancelled()) return null;
	    		
	    	    bitmap = BitmapFactory.decodeByteArray(baf.toByteArray(), 0, baf.length());
	    	} catch (IOException e) {
				Log.e(TAG, "error creating InputStream", e);
			} finally {
				if (urlConnection != null) urlConnection.disconnect();
				if (httpStream != null) {
					try { httpStream.close(); } catch (IOException e) { Log.e(TAG, "IOException while closing http stream", e); }
				}
			}

	    	return bitmap;
	    }
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
}
