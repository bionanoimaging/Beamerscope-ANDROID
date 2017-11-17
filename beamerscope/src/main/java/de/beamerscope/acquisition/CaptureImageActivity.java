package de.beamerscope.acquisition;

/**
 * Created by Bene on 26.09.16.
 */


import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import de.beamerscope.R;


//Reference : https://github.com/googlesamples/android-Camera2Basic

public class CaptureImageActivity extends Activity {

    String directoryPath; //Path of the directory in which all output images will be stored
    String TAG = "CaptureImageActivity";

    //Start CaptureImageFragment to open the camera preview for capturing the photo
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_capture_image);
        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction().replace(R.id.container, CaptureImageFragment.newInstance()).commit();
        }
    }

    //Get the path of the directory from captureImageFragment and pass it to DetectBarcodeActivity.
    public void getDirectoryPath(String path)
    {
        directoryPath = path;
    }

    /*Get the Uri of the image cropped by the user and pass it to DetectBarcodeActivity
      Cropping Library used: https://github.com/ArthurHub/Android-Image-Cropper
     */

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {

    }
}
