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

package com.bitfable.ammocache;

import android.app.Activity;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.bitfable.ammocache.download.ImageDownloader;

public class LaunchActivity extends Activity {
	public static final String TAG = "LaunchActivity";
	private ImageDownloader mImageDownloader;
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        mImageDownloader = new ImageDownloader();
        
        addImage("http://farm7.staticflickr.com/6062/6057826969_bc1373c369_s.jpg");
        addImage("http://farm7.staticflickr.com/6204/6057823753_3bd028649a_s.jpg");
        addImage("http://farm7.staticflickr.com/6186/6058367412_4b06134aff_s.jpg");
        addImage("http://farm7.staticflickr.com/6182/6058363592_7e23272dd7_s_d.jpg");
        addImage("http://farm7.staticflickr.com/6076/6058361608_b909bcc1f2_s_d.jpg");
        addImage("http://farm7.staticflickr.com/6208/6057813451_fb318bf0f0_s_d.jpg");
    }
    
    private void addImage(String imageUrl) {
    	ImageView imageView = new ImageView(this);
    	
    	ViewGroup container = (ViewGroup) findViewById(R.id.container);
    	container.addView(imageView);
    	
    	mImageDownloader.download(imageUrl, imageView);
    }
    
}


