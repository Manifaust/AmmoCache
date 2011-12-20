package com.bitfable.ammocache;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bitfable.ammocache.io.FlushedInputStream;

public class LaunchActivity extends Activity {
	public static final String TAG = "LaunchActivity";
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        new ImageDownloadTask("http://farm7.staticflickr.com/6062/6057826969_bc1373c369_s.jpg").execute();
        new ImageDownloadTask("http://farm7.staticflickr.com/6204/6057823753_3bd028649a_s.jpg").execute();
        new ImageDownloadTask("http://farm7.staticflickr.com/6186/6058367412_4b06134aff_s.jpg").execute();
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
    
    private void addImage(Bitmap bitmap) {
    	ImageView imageView = new ImageView(this);
    	imageView.setImageBitmap(bitmap);
    	
    	ViewGroup container = (ViewGroup) findViewById(R.id.container);
    	container.addView(imageView);
    }
    
    private class ImageDownloadTask extends AsyncTask<Void, Void, Bitmap> {
    	private String mImageUrl;

		public ImageDownloadTask(String imageUrl) {
    		mImageUrl = imageUrl;
    	}

		@Override
		protected Bitmap doInBackground(Void... params) {
			return downloadImage(mImageUrl);
		}
    	
		@Override
		protected void onPostExecute(Bitmap bitmap) {
			if (bitmap != null) {
				addImage(bitmap);
			} else {
				Log.w(TAG, "could not download bitmap");
			}
		}
    }
}


