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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
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
	private static final String TAG = "ImageDownloader";
	private static final long CACHE_SIZE = 10 * 1024 * 1024; // 10 MiB
	private static final String CACHE_FILE_NAME = "image_downloader_cache";
	
	public ImageDownloader() {
		this(null);
	}
	
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
	     if (cancelPotentialDownload(imageUrl, imageView)) {
	         ImageDownloadTask task = new ImageDownloadTask(imageUrl, imageView);
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
	
    private static Bitmap downloadImage(String imageUrl) {
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
    	try {
    		InputStream in = new FlushedInputStream(urlConnection.getInputStream());
    	    bitmap = BitmapFactory.decodeStream(in);
    	} catch (IOException e) {
			Log.e(TAG, "error creating InputStream", e);
		} finally {
			if (urlConnection != null) urlConnection.disconnect();
		}

    	return bitmap;
    }

    private static final class ImageDownloadTask extends AsyncTask<Void, Void, Bitmap> {
    	String url;
    	private WeakReference<ImageView> mImageViewReference;

		public ImageDownloadTask(String imageUrl, ImageView imageView) {
    		url = imageUrl;
    		mImageViewReference = new WeakReference<ImageView>(imageView);
    	}

		@Override
		protected Bitmap doInBackground(Void... params) {
			return downloadImage(url);
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
}
