package de.beamerscope.nativepart;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

/**
 * Created by Bene on 25.11.2015.
 */
public class NativePart {
    static
    {
        try{
            System.loadLibrary("native_microscope");
            System.loadLibrary("opencv_java3");
        }
        catch(Exception e){
            Log.e("Error Load Library", String.valueOf(e));
        }
    }

    public static native void qDPC( long addrI11, long addrI12, long addrI21,
                                    long addrI22, long addrtrans1, long addrtrans2,
                                    long addrOutputMat,
                                    long addrOutputMatDiffX,
                                    long addrOutputMatDiffY);

    public static native int init(AssetManager assetManager, String model);

    public static native void getFFTVector(long addrInputMat, long addrOutputMat);

    public static native void getAngleVector(long addrInputMat, long addrOutputMat);

    /**
     * draw pixels
     */
    public static native void getOptimizedParams(long inputAdrr, long outputAdrr);

    public static boolean setup(Context context) {
        AssetManager assetManager = context.getAssets();

        // model from beginner tutorial
        //int ret = init(assetManager, "file:///android_asset/beginner-graph.pb");

        // model from expert tutorial
        int ret = init(assetManager, "file:///android_asset/expert-graph.pb");
        //int ret = init(assetManager, "file:///storage/emulated/0/Beamerscope/TF/expert-graph.pb");
        return ret >= 0;
    }



}

