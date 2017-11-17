package de.beamerscope.calibration;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.DialogFragment;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRouter;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.appindexing.Action;
import com.google.android.gms.appindexing.AppIndex;
import com.google.android.gms.appindexing.Thing;
import com.google.android.gms.common.api.GoogleApiClient;

import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import de.beamerscope.R;
import de.beamerscope.datasets.Dataset;
import de.beamerscope.presentation.MyPresentation;
import de.beamerscope.utils.CreatePatterns;

/**
 * Created by Bene on 13.12.16.
 */

public class GammaCalibActivity extends Activity implements GammaCalibSettings.NoticeDialogListener {

    //*************************************************
    public MediaRouter mMediaRouter;

    // Active Presentation, set to null if no secondary screen is enabled
    public MyPresentation mPresentation;

    public Bitmap mBitmap = null;
    //*************************************************

    public String acquireType = "MultiMode";


    public double objectiveNA = 0.1;
    public double brightfieldNA = 0.4; // Account for LED size to be sure we completly cover NA .025

    public boolean cameraReady = true;
    public int nMaxSearchApertures = 5;     // number of images for calibrating in x/y (search for max intensity)
    public int nMaxSearchGamma = 10;        // number of images to search for gamma fit-values (n<15, otherwise Memory leark?!
    int dx = 15;                            // spacing between sub-apertures in x-directions
    int dy = 15;                            // spacing between sub-apertures in y-directions

    public float mmDelay = 0.0f;
    public int aecCompensation = 0;
    public String datasetName = "Dataset";
    public boolean usingHDR = false;
    public Dataset mDataset;
    public int isoSetting = 200;

    public Rect nRect = new Rect(0, 0, 100, 100);

    public DialogFragment settingsDialogFragment;

    public Button btnShowMaxIntensity;
    public SeekBar exposureSlider;
    public TextView exposureValue;

    public int initialExposureValue = 10;
    public int exposure_level = 10;

    public int maxNodesLogit = 100;



    Button btnCalibrate;
    Button btnChart;
    public TextView acquireTextView;
    public TextView acquireTextView2;
    public TextView timeLeftTextView;

    public ProgressBar acquireProgressBar;

    public double meanVal = 0.;
    List<Double> maxValList = new ArrayList<Double>();
    List<Double> gammaValList = new ArrayList<Double>();
    List<Double> globalLogit = new ArrayList<Double>();
    int maxIntensityIndex = -1;

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public GoogleApiClient client;


    //**********************************************************************************************
    //  Method OnCreate
    //**********************************************************************************************


    public GammaCalibActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_gamma_calib);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        // Initialize shared preferences to save values permanently
        SharedPreferences prefs = getSharedPreferences("beamerscope_calibration", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();


        // load native libs
        System.loadLibrary("native_microscope");
        //System.loadLibrary("libopencv_java3");

        // BEGIN_INCLUDE(getMediaRouter)
        // Get the MediaRouter service
        mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
        // END_INCLUDE(getMediaRouter)


        // get acquire type from overlaying activity
        Bundle b = getIntent().getExtras();
        if (b != null) {
            acquireType = (String) b.get("type");
        }
        Log.i("Selected Acquire Type", acquireType);


        mTextureView = (AutoFitTextureView) findViewById(R.id.texture);


        /*
        1. Exposure using slider (seekbar)
        The seekbar value can change from 0% to 100%
        where 0% corresponds to zoom_level 1x and 100% corresponds to maximum zoom allowed by phone camera
     */

        exposureSlider = (SeekBar) findViewById(R.id.seekBarExp);
        exposureSlider.setMax(500);
        exposureSlider.setProgress(initialExposureValue);

        exposureValue = (TextView) findViewById(R.id.textViewExp);
        exposureValue.setText("Exposuretime: " + initialExposureValue + "ms");

        exposureSlider.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {

                    int progressValueExposure; //progress value of seekbar

                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        progressValueExposure = progress;
                        exposureValue.setText("Exposuretime: " + progress + "ms");
                        slideToExposuretime(progressValueExposure);
                    }


                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        exposureValue.setText("Exposuretime: " + progressValueExposure + "ms");
                        slideToExposuretime(progressValueExposure);
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        exposureValue.setText("Exposuretime: " + progressValueExposure + "ms");
                        slideToExposuretime(progressValueExposure);
                    }
                }
        );



        //Create second surface with another holder (holderTransparent) for drawing the rectangle
        SurfaceView transparentView = (SurfaceView) findViewById(R.id.TransparentView);
        transparentView.setBackgroundColor(Color.TRANSPARENT);
        transparentView.setZOrderOnTop(true);    // necessary
        SurfaceHolder holderTransparent = transparentView.getHolder();
        holderTransparent.setFormat(PixelFormat.TRANSPARENT);
        //TODO holderTransparent.addCallback(callBack);


        //Set Text and Button in UI
        btnShowMaxIntensity = (Button) findViewById(R.id.btnShowMaxIntensity);
        btnCalibrate = (Button) findViewById(R.id.btnSaveFrame);
        btnChart = (Button) findViewById(R.id.btnChart);

        acquireTextView = (TextView) findViewById(R.id.acquireStatusTextView);
        acquireTextView2 = (TextView) findViewById(R.id.acquireStatusTextView2);
        timeLeftTextView = (TextView) findViewById(R.id.timeLeftTextView);
        acquireProgressBar = (ProgressBar) findViewById(R.id.acquireProgressBar);

        acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

        //Setup Colours
        acquireTextView.setTextColor(Color.YELLOW);
        acquireTextView2.setTextColor(Color.YELLOW);
        timeLeftTextView.setTextColor(Color.YELLOW);

        //getActionBar().setDisplayHomeAsUpEnabled(true);

        settingsDialogFragment = new GammaCalibSettings();


        btnCalibrate.setOnClickListener(new View.OnClickListener() {
                                          public void onClick(View v) {
                                              Log.i(TAG, acquireType);
                                              objectiveNA = brightfieldNA;
                                              new de.beamerscope.calibration.GammaCalibActivity.findMax(v.getContext()).execute();
                                          }
                                      }
        );


        btnShowMaxIntensity.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            // In parent activity
            Intent intent = new Intent(v.getContext(), de.beamerscope.calibration.GammaCalibMaxIntMap.class);
            Bundle extraValues = new Bundle();
            extraValues.putInt("nValues", maxValList.size()); // Add knowledge about the number of elements
            intent.putExtras(extraValues);
            startActivity(intent);
            }
        });



        btnChart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // In parent activity
                Intent intent = new Intent(v.getContext(), de.beamerscope.calibration.GammaCalibChart.class);
                startActivity(intent);
        }
        });




        // BEGIN_INCLUDE(getMediaRouter)
        // Get the MediaRouter service
        mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
        // END_INCLUDE(getMediaRouter)


        mBitmap = CreatePatterns.getCircle(0, 0, 40);


/*
        try {
            InputStream bitmap=getAssets().open("Beamerscope_1.PNG");
            mBitmap = BitmapFactory.decodeStream(bitmap);
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
*/


        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client = new GoogleApiClient.Builder(this).addApi(AppIndex.API).build();
    }

    @Override
    protected void onPause() {

        Log.e(TAG, "onPause");

        closeCamera();

        stopBackgroundThread();
        super.onPause();

        // BEGIN_INCLUDE(onPause)
        // Stop listening for changes to media routes.
        mMediaRouter.removeCallback(mMediaRouterCallback);
        // END_INCLUDE(onPause)
    }

    @Override
    public void onResume() {
        super.onResume();

        // BEGIN_INCLUDE(addCallback)
        // Register a callback for all events related to live video devices
        mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
        // END_INCLUDE(addCallback)

        // Update the displays based on the currently active routes
        updatePresentation();

        startBackgroundThread();

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (mTextureView.isAvailable()) {
            openCamera(mTextureView.getWidth(), mTextureView.getHeight());
        } else {
            mTextureView.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    public void onDestroy() {
        super.onDestroy();

    }


    //**********************************************************************************************
    //  DialogStuff
    //**********************************************************************************************


    @Override
    public void onDialogPositiveClick(DialogFragment dialog) {
    }

    @Override
    public void onDialogNegativeClick(DialogFragment dialog) {
    }

    /**
     * ATTENTION: This was auto-generated to implement the App Indexing API.
     * See https://g.co/AppIndexing/AndroidStudio for more information.
     */
    public Action getIndexApiAction() {
        Thing object = new Thing.Builder()
                .setName("GammaCalibActivity Page") // TODO: Define a title for the content shown.
                // TODO: Make sure this auto-generated URL is correct.
                .setUrl(Uri.parse("http://[ENTER-YOUR-URL-HERE]"))
                .build();
        return new Action.Builder(Action.TYPE_VIEW)
                .setObject(object)
                .setActionStatus(Action.STATUS_TYPE_COMPLETED)
                .build();
    }

    @Override
    public void onStart() {
        super.onStart();

        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        client.connect();
        AppIndex.AppIndexApi.start(client, getIndexApiAction());
    }

    @Override
    public void onStop() {
        super.onStop();
        // ATTENTION: This was auto-generated to implement the App Indexing API.
        // See https://g.co/AppIndexing/AndroidStudio for more information.
        AppIndex.AppIndexApi.end(client, getIndexApiAction());
        client.disconnect();

        // BEGIN_INCLUDE(onStop)
        // Dismiss the presentation when the activity is not visible.
        if (mPresentation != null) {
            mPresentation.dismiss();
            mPresentation = null;
        }
        // BEGIN_INCLUDE(onStop)
    }



    //------------------------CAMERA 2 API STUFF----------------------------------------------------
    //Conversion from screen rotation to JPEG orientation.

    public static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    public static final int REQUEST_PERMISSION = 1;
    public static final String FRAGMENT_DIALOG = "dialog";

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    public static final String TAG = "Camera2BasicFragment";

    // Camera state: Showing camera preview.
    public static final int STATE_PREVIEW = 0;

    // * Camera state: Waiting for the focus to be locked.
    public static final int STATE_WAITING_LOCK = 1;

    // Camera state: Waiting for the exposure to be precapture state.
    public static final int STATE_WAITING_PRECAPTURE = 2;

    // Camera state: Waiting for the exposure state to be something other than precapture.
    public static final int STATE_WAITING_NON_PRECAPTURE = 3;

    // Camera state: Picture was taken.
    public static final int STATE_PICTURE_TAKEN = 4;

    // Max preview width that is guaranteed by Camera2 API
    public static final int MAX_PREVIEW_WIDTH = 1920;

    //* Max preview height that is guaranteed by Camera2 API
    public static final int MAX_PREVIEW_HEIGHT = 1080;

    //TextureView.SurfaceTextureListener handles several lifecycle events on a TextureView.
    public final TextureView.SurfaceTextureListener mSurfaceTextureListener
            = new TextureView.SurfaceTextureListener() {

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
            openCamera(width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture texture) {
        }

    };

    //ID of the current CameraDevice.
    public String mCameraId;

    //AutoFitTextureView for camera preview.
    AutoFitTextureView mTextureView;

    //A CameraCaptureSession for camera preview.
    public CameraCaptureSession mCaptureSession;

    //A reference to the opened CameraDevice
    public CameraDevice mCameraDevice;

    //The android.util.Size of camera preview.
    public Size mPreviewSize;

    /**
     * {@link CameraDevice.StateCallback} is called when {@link CameraDevice} changes its state.
     */
    public final CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {

        @Override
        public void onOpened(@NonNull CameraDevice cameraDevice) {
            // This method is called when the camera is opened.  We start camera preview here.
            mCameraOpenCloseLock.release();
            mCameraDevice = cameraDevice;
            createCameraPreviewSession();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            mCameraDevice = null;


        }

    };



    /***
     * An additional thread for running tasks that shouldn't block the UI.
     */
    public HandlerThread mBackgroundThread;

    /***
     * A {@link Handler} for running tasks in the background.
     */
    public Handler mBackgroundHandler;

    /***
     * An {@link ImageReader} that handles still image capture.
     */
    public ImageReader mImageReader;
    /**
     * This a callback object for the {@link ImageReader}. "onImageAvailable" will be called when a
     * still image is ready to be saved.
     */

        public final ImageReader.OnImageAvailableListener mOnImageAvailableListener
            = new ImageReader.OnImageAvailableListener() {


        @Override
        public void onImageAvailable(ImageReader reader) {
            Image image = null;

            image = reader.acquireLatestImage();

            ByteBuffer buf = image.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buf.remaining()]; // bytes contains the raw bytes from the image
            buf.get(bytes);

            int width = image.getWidth();
            int height = image.getHeight();
            Mat greyMat = new Mat(height, width, CvType.CV_8UC1);
            greyMat.put(0, 0, bytes);

            meanVal = Core.mean(greyMat).val[0];
            Log.i(TAG, "Mean Value of MAT is:"+String.valueOf(meanVal));
            //Imgcodecs.imwrite(String.valueOf(full_image_file), greyMat);

            image.close();

            if (image != null) {
                image.close();
                cameraReady = true;
            }



        }
    };

    /**
     * {@link CaptureRequest.Builder} for the camera preview
     */
    public CaptureRequest.Builder mPreviewRequestBuilder;

    /**
     * {@link CaptureRequest} generated by {@link #mPreviewRequestBuilder}
     */
    public CaptureRequest mPreviewRequest;

    /**
     * The current state of camera state for taking pictures.
     *
     * @see #mCaptureCallback
     */
    public int mState = STATE_PREVIEW;

    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    public Semaphore mCameraOpenCloseLock = new Semaphore(1);
    
    /**
     * Orientation of the camera sensor
     */
    public int mSensorOrientation;

    /**
     * A {@link CameraCaptureSession.CaptureCallback} that handles events related to JPEG capture.
     */
    public CameraCaptureSession.CaptureCallback mCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        public void process(CaptureResult result) {
            switch (mState) {
                case STATE_PREVIEW: {
                    // We have nothing to do when the camera preview is working normally.
                    break;
                }
                case STATE_WAITING_LOCK: {
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState == null) {
                        captureStillPicture();
                    } else if (CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED == afState ||
                            CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED == afState) {
                        // CONTROL_AE_STATE can be null on some devices
                        // Change to CONTROL_AF_STATE
                        Integer aeState = result.get(CaptureResult.CONTROL_AF_STATE);
                        if (aeState == null ||
                                aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED) {
                            mState = STATE_PICTURE_TAKEN;
                            captureStillPicture();
                        } else {
                            runPrecaptureSequence();
                        }
                    }
                    break;
                }
                case STATE_WAITING_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    // Change to CONTROL_AF_STATE
                    Integer aeState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (aeState == null ||
                            aeState == CaptureResult.CONTROL_AE_STATE_PRECAPTURE ||
                            aeState == CaptureRequest.CONTROL_AE_STATE_FLASH_REQUIRED) {
                        mState = STATE_WAITING_NON_PRECAPTURE;
                    }
                    break;
                }
                case STATE_WAITING_NON_PRECAPTURE: {
                    // CONTROL_AE_STATE can be null on some devices
                    // Change to CONTROL_AF_STATE
                    Integer aeState = result.get(CaptureResult.CONTROL_AE_STATE);
                    if (aeState == null || aeState != CaptureResult.CONTROL_AE_STATE_PRECAPTURE) {
                        mState = STATE_PICTURE_TAKEN;
                        captureStillPicture();
                    }
                    break;
                }
            }
        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session,
                                        @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                       @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    /**
     * Shows a {@link Toast} on the UI thread.
     *
     * @param text The message to show
     */
    public void showToast(final String text) {
        //final Activity activity = getActivity();
        if (this != null) {
            this.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    //Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Given {@code choices} of {@code Size}s supported by a camera, choose the smallest one that
     * is at least as large as the respective texture view size, and that is at most as large as the
     * respective max size, and whose aspect ratio matches with the specified value. If such size
     * doesn't exist, choose the largest one that is at most as large as the respective max size,
     * and whose aspect ratio matches with the specified value.
     *
     * @param choices           The list of sizes that the camera supports for the intended output
     *                          class
     * @param textureViewWidth  The width of the texture view relative to sensor coordinate
     * @param textureViewHeight The height of the texture view relative to sensor coordinate
     * @param maxWidth          The maximum width that can be chosen
     * @param maxHeight         The maximum height that can be chosen
     * @param aspectRatio       The aspect ratio
     * @return The optimal {@code Size}, or an arbitrary one if none were big enough
     */
    public static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth, int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CaptureImageFragment.CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CaptureImageFragment.CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }



    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    public void setUpCameraOutputs(int width, int height) {
        //Activity activity = getActivity();
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }

                // For still image captures, we use the largest available size.
                Size smallest = Collections.min(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), new CaptureImageFragment.CompareSizesByArea());
                mImageReader = ImageReader.newInstance(smallest.getWidth(), smallest.getHeight(),
                        ImageFormat.YUV_420_888, /*maxImages*/2);

                //reader = ImageReader.newInstance(previewSize.getWidth(), previewSize.getHeight(), ImageFormat.YUV_420_888, 2);

                mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = this.getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }

                Point displaySize = new Point();
                this.getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, smallest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    mTextureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }

                // Check if the flash is supported.
                //Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                //mFlashSupported = available == null ? false : available;

                mCameraId = cameraId;
                return;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException e) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            //TODO CaptureImageFragment.ErrorDialog.newInstance(getString(R.string.camera_error)).show(getChildFragmentManager(), FRAGMENT_DIALOG);
        }
    }

    /**
     * Opens the camera specified
     */
    public void openCamera(int width, int height) {

        if ((ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                || (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {
            //TODO requestForPermission();
            return;
        }

        setUpCameraOutputs(width, height);
        //Activity activity = getActivity();
        CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Closes the current {@link CameraDevice}.
     */
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCameraDevice != null) {
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    /**
     * Starts a background thread and its {@link Handler}.
     */
    public void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }

    /**
     * Stops the background thread and its {@link Handler}.
     */
    public void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Creates a new {@link CameraCaptureSession} for camera preview.
     */
    public void createCameraPreviewSession() {
        try {
            SurfaceTexture texture = mTextureView.getSurfaceTexture();
            assert texture != null;

            // We configure the size of default buffer to be the size of camera preview we want.
            texture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            // This is the output Surface we need to start preview.
            Surface surface = new Surface(texture);

            // We set up a CaptureRequest.Builder with the output Surface.
            mPreviewRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewRequestBuilder.addTarget(surface);


            // Here, we create a CameraCaptureSession for camera preview.
            mCameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {

                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {

                            // The camera is already closed
                            if (mCameraDevice == null) {
                                return;
                            }

                            // When the session is ready, we start displaying the preview.
                            mCaptureSession = cameraCaptureSession;
                            try {


                                //SETUP Everything in advance
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_MODE_OFF);
                                mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);

                                mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);

                                // Finally, we start displaying the camera preview.
                                mPreviewRequest = mPreviewRequestBuilder.build();
                                mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                @NonNull CameraCaptureSession cameraCaptureSession) {
                            showToast("Failed");
                        }
                    }, null
            );
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Initiate a still image capture.
     */
    public void takePicture() {
        cameraReady = false;
        try {
            //Lock the focus as the first step for a still image capture.
            // This is how to tell the camera to lock focus.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the lock.
            mState = STATE_WAITING_LOCK;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Run the precapture sequence for capturing a still image. This method should be called when
     * we get a response in {@link #mCaptureCallback} from {@link #takePicture()}.
     */
    public void runPrecaptureSequence() {
        try {
            // This is how to tell the camera to trigger.
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);
            mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) exposure_level * 1000000);
            mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);

            // Tell #mCaptureCallback to wait for the precapture sequence to be set.
            mState = STATE_WAITING_PRECAPTURE;
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback,
                    mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Capture a still picture. This method should be called when we get a response in
     * {@link #mCaptureCallback} from both {@link #takePicture()}.
     */
    public void captureStillPicture() {
        try {
            //final Activity activity = getActivity();
            if (null == this || null == mCameraDevice) {
                return;
            }
            // This is the CaptureRequest.Builder that we use to take a picture.
            final CaptureRequest.Builder captureBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureBuilder.addTarget(mImageReader.getSurface());

            //Apply Preview Settings to actuqal acquisition
            captureBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoSetting);
            captureBuilder.set(CaptureRequest.CONTROL_EFFECT_MODE, CameraMetadata.CONTROL_EFFECT_MODE_MONO);
            captureBuilder.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF);
            captureBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CameraMetadata.CONTROL_AE_ANTIBANDING_MODE_OFF);
            captureBuilder.set(CaptureRequest.CONTROL_AE_MODE,CameraMetadata.CONTROL_AE_MODE_OFF);
            captureBuilder.set(CaptureRequest.CONTROL_AWB_MODE, CameraMetadata.CONTROL_AWB_MODE_OFF);
            captureBuilder.set(CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_OFF);
            captureBuilder.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF);
            captureBuilder.set(CaptureRequest.SCALER_CROP_REGION, nRect);

            //set camera exposure time (default is 1000000 ns)
            captureBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long)  exposure_level * 1000000);


            // Orientation
            int rotation = this.getWindowManager().getDefaultDisplay().getRotation();
            captureBuilder.set(CaptureRequest.JPEG_ORIENTATION, getOrientation(rotation));

            CameraCaptureSession.CaptureCallback CaptureCallback
                    = new CameraCaptureSession.CaptureCallback() {

                @Override
                public void onCaptureCompleted(@NonNull CameraCaptureSession session,
                                               @NonNull CaptureRequest request,
                                               @NonNull TotalCaptureResult result) {
                    unlockFocus();
                }
            };

            mCaptureSession.stopRepeating();
            mCaptureSession.capture(captureBuilder.build(), CaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieves the JPEG orientation from the specified screen rotation.
     *
     * @param rotation The screen rotation.
     * @return The JPEG orientation (one of 0, 90, 270, and 360)
     */
    public int getOrientation(int rotation) {
        // Sensor orientation is 90 for most devices, or 270 for some devices (eg. Nexus 5X)
        // We have to take that into account and rotate JPEG properly.
        // For devices with orientation of 90, we simply return our mapping from ORIENTATIONS.
        // For devices with orientation of 270, we need to rotate the JPEG 180 degrees.
        return (ORIENTATIONS.get(rotation) + mSensorOrientation + 270) % 360;
    }

    /**
     * Unlock the focus. This method should be called when still image capture sequence is
     * finished.
     */

    public void unlockFocus() {
        try {
            // Reset the auto-focus trigger
            //mPreviewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
            //setAutoFlash(mPreviewRequestBuilder);
            mCaptureSession.capture(mPreviewRequestBuilder.build(), mCaptureCallback, mBackgroundHandler);
            // After this, the camera will go back to the normal state of preview.
            mState = STATE_PREVIEW;
            mCaptureSession.setRepeatingRequest(mPreviewRequest, mCaptureCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }



    public void toggleBrightfield() {
        acquireType = "BFMode";
    }

    public void toggleDPC(){
        acquireType = "DPCMode";
    }

    public void toggleDarkfield() {
        acquireType = "DFMode";
    }

    public void toggleFPM() {
        acquireType = "FPMMode";
    }

    public void toggleOPT(){
        acquireType = "MultiMode";
    }



    
    // Own methods:

    //------------------------------------------------------------------------------------------------
    //
    /* Add ExposureTime functionality (26/9/2016)
    1. Varry Exposure Time using slider
     */

    //Get maximum exposuretime and sensor info of phone camera

    int upperExposuretime, lowerExposuretime;
    //long exposureTime;

    public void getExposuretimeCapability() {
        try {

            Object[] possibleValuesExposuretime = new Object[100];

            //Activity activity = getActivity();
            CameraManager manager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(mCameraId);


            Range<Long> exposureRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);

            lowerExposuretime = (int) (exposureRange.getLower() / (long) 1000000) + 1;
            upperExposuretime = (int) ((exposureRange.getUpper()/10) / (long) 1000000); // upper Limit is far to high


            int range = upperExposuretime - lowerExposuretime;

            int incrementer = (int) (range / 100.0);


            for (int i = 0; i < 100; i++) {
                Integer value = i * incrementer + lowerExposuretime;
                possibleValuesExposuretime[i] = new Pair<Integer, String>(value, value.toString() + "ms");
            }


        } catch (CameraAccessException e) {
            throw new RuntimeException("can not access camera.", e);
        }
    }


    //1. Zoom the preview according to the progress of the zoom slider
    public void slideToExposuretime(int exposuretime_val) {
        /*
          exposuretime_val is the exposure-time percentage displayed on the textView (a value between 0 and 100)
          exposuretime_val = 0 implies exposure_level = 1 ms
          exposuretime_val = 100 implies exposure_level = maxexposuretime
          Solve the straight line equation
        */
        getExposuretimeCapability();
        exposure_level = (int) ((((upperExposuretime - lowerExposuretime) * exposuretime_val) / 99) + 1);

        Log.i(TAG, "Exposuretime: " + String.valueOf(exposure_level));

        //set camera exposure time (default is 1000000 ns)
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, (long) exposure_level * 1000000);

        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }

    }

    public class findMax extends AsyncTask<Void, Void, Void> {

        public Context mContext;

        public findMax (Context context){
            mContext = context;
        }

        int n = 0;
        long t = 0;

        // Variable for X/Y-pairs for NA positions
        ArrayList <Double> maxValPosX = new ArrayList<Double>();
        ArrayList <Double> maxValPosY = new ArrayList<Double>();

        // Add points here; for instance,
        //WeightedObservedPoint point =
        //                                  Weight, X, Y
        MyFuncFitter fitter = new MyFuncFitter();
        ArrayList<WeightedObservedPoint> points = new ArrayList<WeightedObservedPoint>();


        String path = "/Beamerscope/CALIB_" + datasetName + "/";
        File myDir = new File(Environment.getExternalStorageDirectory() + path);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            if (!myDir.exists()) {
                if (!myDir.mkdirs()) {
                    return; //Cannot make directory
                }
            }

            // reinitilaize gammaValList in case it has been used before
            gammaValList.clear();
            maxValList.clear();
            globalLogit.clear();

            timeLeftTextView.setText("Time left:");
            acquireTextView.setText(String.format("Acquiring - MODE: %s", acquireType));
            acquireProgressBar.setVisibility(View.VISIBLE); // Make invisible at first, then have it pop up
            acquireProgressBar.setMax(nMaxSearchApertures * nMaxSearchApertures);


            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mDataset.DATASET_PATH = Environment.getExternalStorageDirectory() + path;
            //mDataset.DATASET_TYPE = acquireType;

            updatePresentation();
        }

        @Override
        protected void onProgressUpdate(Void... params) {

            // make sure to close the current Presentation, otherwise all instances will eat up the
            // memory... TODO: make something like an update rather than opening/closing?
            //updatePresentation();
            showNextColor();

            acquireProgressBar.setProgress(n);
            long elapsed = SystemClock.elapsedRealtime() - t;
            t = SystemClock.elapsedRealtime();
            float timeLeft = (float) (((long) (nMaxSearchApertures * nMaxSearchApertures - n) * elapsed) / 1000.0);
            timeLeftTextView.setText(String.format("Time left: %.2f seconds, %d/%d images saved", timeLeft, n, 5 * nMaxSearchApertures));
            Log.d(TAG, String.format("Time left: %.2f seconds", timeLeft));



        }


        public int getMaxIndex(List<Double> list){
            double max = Double.MIN_VALUE;
            int maxIndex = 0;
            for(int i=0; i<list.size(); i++){
                if(list.get(i) > max){
                    max = list.get(i);
                    maxIndex = i;
                }
            }
            return maxIndex;
        }


        @Override
        protected Void doInBackground(Void... params) {

            t = SystemClock.elapsedRealtime();


            int radius_NA = 25;        // radius of illumination sub-aperture

            int cx_max = 0;
            int cy_max = 0;
            double lastmeanVal = -100000000;

            int cx, cy;

            publishProgress();

            // Wait for the data to propigate down the chain
            t = SystemClock.elapsedRealtime();
            long startTime = SystemClock.elapsedRealtime();

            for (int nx = 0; nx < nMaxSearchApertures; nx++) {
                for (int ny = 0; ny < nMaxSearchApertures; ny++) {

                    // Show point sources and look where the intensity is highest => centre of the NA of the condenser
                    cx = (int)(nx- nMaxSearchApertures /2)*dx;
                    cy = (int)(ny- nMaxSearchApertures /2)*dy;

                    Log.e(TAG, String.valueOf(cx)+ ", "+ String.valueOf(cy));
                    mBitmap = CreatePatterns.getCircle(cx, cy, radius_NA);

                    publishProgress();
                    mSleep(250); // wait for Presenation API to settle

                    cameraReady = false;
                    captureImage();

                    while(!cameraReady)
                    {
                        mSleep(10);
                    }

                    if (meanVal>lastmeanVal) {
                        cx_max = cx;
                        cy_max = cy;

                        lastmeanVal = meanVal;
                    }

                    // add all mean Values to List
                    maxValList.add(meanVal);

                    // add all X/Y pairs to a list, save for calibration
                    maxValPosX.add((double)cx);
                    maxValPosY.add((double)cy);

                    n++;

                    // free memory manually
                    System.gc();
                }
            }


            // Measure the Gamma-Curve of the CAMERA-LCD interaction => linearize
            for (int ii = 0; ii < nMaxSearchGamma; ii++) {

                // write image to second display and wait. Presentation API is not real time!
                try {
                    // this generates a gray-image ramp to measure the intensitiy-values depending
                    // on the digital gray-value
                    mBitmap = CreatePatterns.getCircle(cx_max, cy_max, radius_NA, (double)ii/(double)nMaxSearchGamma);
                    publishProgress();
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }


                cameraReady = false;
                captureImage();
                while(!cameraReady)
                {
                    mSleep(1);
                }

                // add all mean Values to List
                gammaValList.add(meanVal);

                // add point to the Gamma-List      Weight, X, Y
                points.add(new WeightedObservedPoint(1.0, n, meanVal));
                n++;

                // free memory manually
                System.gc();

            }

            return null;
        }


        @Override
        protected void onPostExecute(Void result) {
            super.onPostExecute(result);
            acquireProgressBar.setVisibility(View.INVISIBLE); // Make invisible at first, then have it pop up

            //String cmd = String.format("p%d", centerLED);
            timeLeftTextView.setText(" ");


            // Fit the intensity values to a sigmoid function -> best fit for LCD behaviour
            final double coeffs[] = fitter.fit(points);
            Log.i(TAG, Arrays.toString(coeffs));


            // get iterator with highest intensity
            maxIntensityIndex = getMaxIndex(maxValList);
            Log.i(TAG, "MaxIndex: "+maxIntensityIndex);


            // normalize maxValues
            for (int xi = 0; xi< nMaxSearchApertures * nMaxSearchApertures; xi++){
                double maxVal = maxValList.get(xi)/maxValList.get(maxIntensityIndex)*100;
                Log.i(TAG, "Maxint "+String.valueOf(xi)+" "+String.valueOf(maxVal));
                maxValList.set(xi, maxVal);
            }


            // Calculate some output-numbers of the inverse-fitted sigmoid and save
            for (int xi=0; xi<maxNodesLogit; xi++){
                globalLogit.add(Logit.logit((double) xi, coeffs));
                Log.i(TAG, "Gamma "+String.valueOf(xi)+" "+String.valueOf(globalLogit.get(xi)));
            }

            // Save the max intensities and gamma-values to disk
            saveArray("Maxint", maxValList);
            saveArray("Gamma", globalLogit);

            // Save X/Y position pairs of max intensities
            saveArray("maxValPosX", maxValPosX);
            saveArray("maxValPosY", maxValPosY);


            saveValue("Centerposition", maxIntensityIndex);

            // free memory
            System.gc();

            // Make invisible at first, then have it pop up
            acquireProgressBar.setVisibility(View.INVISIBLE);


        }

        void mSleep(int sleepVal) {
            try {
                Thread.sleep(sleepVal);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        boolean saveArray(String key, List<Double> value)
        {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
            SharedPreferences.Editor mEdit1 = sp.edit();

            /* sKey is an array */
            mEdit1.putInt("Status_size_"+key, value.size());

            for(int i=0;i<value.size();i++)
            {
                mEdit1.remove(key + i);
                mEdit1.putString(key + i, String.valueOf(value.get(i)));
            }

            return mEdit1.commit();
        }


        boolean saveValue(String key, double value)
        {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(mContext);
            SharedPreferences.Editor mEdit1 = sp.edit();

            mEdit1.putString(key, String.valueOf(value));
            return mEdit1.commit();
        }

        public void captureImage() {
            takePicture();
        }
    }



    public void openSettingsDialog() {
        settingsDialogFragment.show(getFragmentManager(), "GammaCalibrationSettings");
    }

    public void setDatasetName(String name) {
        datasetName = name;
    }

    public void setNA(double na) {
        brightfieldNA = na;
    }

    public void setISO(String iso) {
        isoSetting = Integer.parseInt(iso);
        Log.i("ISO return", Integer.toString(isoSetting));

        //set camera iso value
        mPreviewRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY, isoSetting);

        try {
            mCaptureSession.setRepeatingRequest(mPreviewRequestBuilder.build(), mCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (NullPointerException ex) {
            ex.printStackTrace();
        }
    }


    /**
     * Implementing a {@link android.media.MediaRouter.Callback} to update the displayed
     * {@link android.app.Presentation} when a route is selected, unselected or the
     * presentation display has changed. The provided stub implementation
     * {@link android.media.MediaRouter.SimpleCallback} is extended and only
     * {@link android.media.MediaRouter.SimpleCallback#onRouteSelected(android.media.MediaRouter, int, android.media.MediaRouter.RouteInfo)}
     * ,
     * {@link android.media.MediaRouter.SimpleCallback#onRouteUnselected(android.media.MediaRouter, int, android.media.MediaRouter.RouteInfo)}
     * and
     * {@link android.media.MediaRouter.SimpleCallback#onRoutePresentationDisplayChanged(android.media.MediaRouter, android.media.MediaRouter.RouteInfo)}
     * are overridden to update the displayed {@link android.app.Presentation} in
     * {@link #updatePresentation()}. These callbacks enable or disable the
     * second screen presentation based on the routing provided by the
     * {@link android.media.MediaRouter} for {@link android.media.MediaRouter#ROUTE_TYPE_LIVE_VIDEO}
     * streams. @
     */
    public final MediaRouter.SimpleCallback mMediaRouterCallback =
            new MediaRouter.SimpleCallback() {

                // BEGIN_INCLUDE(SimpleCallback)
                /**
                 * A new route has been selected as active. Disable the current
                 * route and enable the new one.
                 */
                @Override
                public void onRouteSelected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
                    updatePresentation();
                }

                /**
                 * The route has been unselected.
                 */
                @Override
                public void onRouteUnselected(MediaRouter router, int type, MediaRouter.RouteInfo info) {
                    updatePresentation();

                }

                /**
                 * The route's presentation display has changed. This callback
                 * is called when the presentation has been activated, removed
                 * or its properties have changed.
                 */
                @Override
                public void onRoutePresentationDisplayChanged(MediaRouter router, MediaRouter.RouteInfo info) {
                    updatePresentation();
                }
                // END_INCLUDE(SimpleCallback)
            };

    /**
     * Updates the displayed presentation to enable a secondary screen if it has
     * been selected in the {@link android.media.MediaRouter} for the
     * {@link android.media.MediaRouter#ROUTE_TYPE_LIVE_VIDEO} type. If no screen has been
     * selected by the {@link android.media.MediaRouter}, the current screen is disabled.
     * Otherwise a new {@link MyPresentation} is initialized and shown on
     * the secondary screen.
     */
    public void updatePresentation() {



        // BEGIN_INCLUDE(updatePresentationInit)
        // Get the selected route for live video
        MediaRouter.RouteInfo selectedRoute = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_VIDEO);

        // Get its Display if a valid route has been selected
        Display selectedDisplay = null;
        if (selectedRoute != null) {
            selectedDisplay = selectedRoute.getPresentationDisplay();
        }
        // END_INCLUDE(updatePresentationInit)

        // BEGIN_INCLUDE(updatePresentationDismiss)
        /*
         * Dismiss the current presentation if the display has changed or no new
         * route has been selected
         */
        try{
            Log.i(TAG, "ZZ1 mPresentation != null "+String.valueOf(mPresentation != null));
            Log.i(TAG, "ZZ2 mPresentation.getDisplay() != selectedDisplay "+String.valueOf(mPresentation.getDisplay() != selectedDisplay));

        }
        catch (Exception e){}

        if (mPresentation != null && mPresentation.getDisplay() != selectedDisplay) {
            mPresentation.dismiss();
            mPresentation = null;
        }
        // END_INCLUDE(updatePresentationDismiss)

        // BEGIN_INCLUDE(updatePresentationNew)
        /*
         * Show a new presentation if the previous one has been dismissed and a
         * route has been selected.
         */
        Log.i(TAG, "Start Bitmap set to Presentation Display");

        Log.i(TAG, "ZZ3 mPresentation == null "+String.valueOf(mPresentation == null));
        Log.i(TAG, "ZZ4 selectedDisplay != null "+String.valueOf(selectedDisplay != null));


        if (mPresentation == null && selectedDisplay != null) {

            // Initialise a new Presentation for the Display
            mPresentation = new MyPresentation(this, selectedDisplay);
            mPresentation.setOnDismissListener(mOnDismissListener);

            // Try to show the presentation, this might fail if the display has
            // gone away in the mean time
            try {
                Log.i(TAG, "Bitmap set to Presentation Display");
                mPresentation.show();
                mPresentation.setBitmap(mBitmap);
            } catch (WindowManager.InvalidDisplayException ex) {
                // Couldn't show presentation - display was already removed
                Log.e(TAG, "Error Bitmap set to Presentation Display");
                mPresentation = null;
            }
        }
        // END_INCLUDE(updatePresentationNew)

    }


    /**
     * Listens for dismissal of the {@link MyPresentation} and removes its
     * reference.
     */
    public final DialogInterface.OnDismissListener mOnDismissListener =
            new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    if (dialog == mPresentation) {
                        mPresentation = null;
                    }
                }
            };



    /**
     * Displays the next color on the secondary screen if it is activate.
     * @param
     */
    private void showNextColor() {
        if (mPresentation != null) {
            // a second screen is active and initialized, show the next color
            mPresentation.setBitmap(mBitmap);

    }


    }

}
