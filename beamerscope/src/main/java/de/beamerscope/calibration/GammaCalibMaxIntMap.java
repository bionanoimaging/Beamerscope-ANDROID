package de.beamerscope.calibration;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.WindowManager;
import android.widget.ImageView;

import java.util.ArrayList;

import de.beamerscope.R;
import de.beamerscope.utils.CreatePatterns;

/**
 * Created by Bene on 29.12.16.
 */


public class GammaCalibMaxIntMap extends Activity{

    String TAG = "GammaCalibMaxIntMap";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_crop);

        // extract infos from Intent-Bundle and Shared Preferences
        Bundle extraValues = getIntent().getExtras();
        int nValues = -1; // or other values
        if(extraValues != null)
            nValues  = extraValues.getInt("nValues");

        ArrayList<Double> valList = loadArray("Maxint");

        int nElements = (int)Math.floor(Math.sqrt(valList.size()));

        if(nElements>0){
            Bitmap MaxIntMap = CreatePatterns.drawRectIntensities(nElements, valList);
            ImageView imageView = (ImageView) findViewById(R.id.imageView);
            imageView.setImageBitmap(MaxIntMap);
        }
    }


    ArrayList<Double> loadArray(String key)
    {
        // http://stackoverflow.com/questions/7057845/save-arraylist-to-sharedpreferences
        // method to read an array-list from the preferences
        ArrayList<Double> valList = new ArrayList<Double>();
        SharedPreferences mSharedPreference1 = PreferenceManager.getDefaultSharedPreferences(this);

        int size = mSharedPreference1.getInt("Status_size_"+key, 0);

        for(int i=0;i<size;i++)
        {
            valList.add(Double.parseDouble(mSharedPreference1.getString(key + i, null)));
        }

        return valList;
    }
}
