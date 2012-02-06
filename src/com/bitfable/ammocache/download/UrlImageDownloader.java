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
import android.os.Build;
import android.util.Log;
import android.widget.ImageView;

import com.bitfable.ammocache.io.FlushedInputStream;

/**
 * Use this class to download images and load them onto ImageView instances.
 * You can configure the HTTP cache size used by changing
 * {@link #HTTP_CACHE_SIZE}.
 * 
 * Many of the network optimizations in this code came from an Android Developer
 * Blog article by Jesse Wilson:
 * 
 * http://android-developers.blogspot.com/2011/09/androids-http-clients.html
 * 
 */
public class UrlImageDownloader extends AbstractImageDownloader {
	/**
	 * The size of the cache shared by {@link HttpURLConnection}
	 */
	private static final long HTTP_CACHE_SIZE = 5 * 1024 * 1024; // 5 MiB
	
	public static String TAG = "UrlImageDownloader";
	private static final int BYTE_ARRAY_BUFFER_INCREMENTAL_SIZE = 1048;
	private static final String HTTP_CACHE_FILE_NAME = "image_downloader_http_cache";

	public UrlImageDownloader(Context context) {
		super(context);

		disableConnectionReuseIfNecessary();

		if (context != null) {
			enableHttpResponseCache(context);
		}
	}

	@Override
	protected Bitmap download(String key, WeakReference<ImageView> imageViewRef) {
    	URL url;
    	
		try {
			url = new URL(key);
		} catch (MalformedURLException e) {
			Log.e(TAG, "url is malformed: " + key, e);
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
    	int contentLength;
    	int bytesDownloaded = 0;
    	try {
    		contentLength = urlConnection.getContentLength();
    		httpStream = new FlushedInputStream(urlConnection.getInputStream());
    		ByteArrayBuffer baf = new ByteArrayBuffer(BYTE_ARRAY_BUFFER_INCREMENTAL_SIZE);
    		byte[] buffer = new byte[BYTE_ARRAY_BUFFER_INCREMENTAL_SIZE];
    		while (!isCancelled(imageViewRef)) {
    			int incrementalRead = httpStream.read(buffer);
    			if (incrementalRead == -1) {
    				break;
    			}
    			bytesDownloaded += incrementalRead;
    			if (contentLength > 0 || (bytesDownloaded > 0 && bytesDownloaded == contentLength)) {
    				int progress = bytesDownloaded * 100 / contentLength;
    				publishProgress(progress, imageViewRef);
    			}
    			baf.append(buffer, 0, incrementalRead);
    		}

    		if (isCancelled(imageViewRef)) return null;
    		
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
	        long httpCacheSize = HTTP_CACHE_SIZE;
	        File httpCacheDir = new File(cacheDir, HTTP_CACHE_FILE_NAME);
	        Class.forName("android.net.http.HttpResponseCache")
	            .getMethod("install", File.class, long.class)
	            .invoke(null, httpCacheDir, httpCacheSize);
	    } catch (Exception httpResponseCacheNotAvailable) {
	    	Log.v(TAG, "HttpResponseCache is not available");
	    }
	}
	
}
