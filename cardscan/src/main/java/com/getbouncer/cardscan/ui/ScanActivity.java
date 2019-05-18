/*
 * Copyright 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.getbouncer.cardscan.ui;


import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.getbouncer.cardscan.CreditCard;
import com.getbouncer.cardscan.CreditCardUtils;
import com.getbouncer.cardscan.DetectedBox;
import com.getbouncer.cardscan.Expiry;
import com.getbouncer.cardscan.ImageUtils;
import com.getbouncer.cardscan.R;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;


public class ScanActivity extends Activity implements Camera.PreviewCallback, View.OnClickListener,
        OnScanListener {

    private Camera mCamera = null;
    private OrientationEventListener mOrientationEventListener;
    private static MachineLearningThread machineLearningThread = null;
    private Semaphore mMachineLearningSemaphore = new Semaphore(1);
    private ImageView mDebugImageView;
    private int mRotation;
    private boolean mSentResponse = false;
    private boolean mIsActivityActive = false;

    public static final String SCAN_RESULT = "creditCard";
    public long errorCorrectionDurationMs = 1500;
    private boolean mIsPermissionCheckDone = false;
    private HashMap<String, Integer> numberResults;
    private HashMap<Expiry, Integer> expiryResults;
    private long firstResultMs = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_capture);

        mOrientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                orientationChanged(orientation);
            }
        };

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 110);
            } else {
                mIsPermissionCheckDone = true;
            }
        }

        findViewById(R.id.flashlightButton).setOnClickListener(this);
        findViewById(R.id.view).getViewTreeObserver().addOnGlobalLayoutListener(new MyGlobalListenerClass());
        mDebugImageView = findViewById(R.id.debugImageView);
    }

    class MyGlobalListenerClass implements ViewTreeObserver.OnGlobalLayoutListener {
        @Override
        public void onGlobalLayout() {
            int[] xy = new int[2];
            View view = findViewById(R.id.view);
            view.getLocationOnScreen(xy);
            // convert from DP to pixels
            int radius = (int) (11 * Resources.getSystem().getDisplayMetrics().density);
            RectF rect = new RectF(xy[0], xy[1],
                    xy[0] + view.getWidth(),
                    xy[1] + view.getHeight());
            Overlay overlay = findViewById(R.id.shadedBackground);
            overlay.setCircle(rect, radius);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mIsPermissionCheckDone = true;
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.setPreviewCallback(null);
            mCamera.release();
        }

        mOrientationEventListener.disable();
        mIsActivityActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIsActivityActive = true;
        numberResults = new HashMap<>();
        expiryResults = new HashMap<>();
        firstResultMs = 0;
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }

        try {
            if (mIsPermissionCheckDone) {
                mCamera = Camera.open();
                setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
                // Create our Preview view and set it as the content of our activity.
                CameraPreview cameraPreview = new CameraPreview(this);
                FrameLayout preview = (FrameLayout) findViewById(R.id.texture);
                preview.addView(cameraPreview);
                mCamera.setPreviewCallback(this);
            }
        } catch (Exception e){
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("Another app is using the camera")
                    .setTitle("Can't open camera");
            builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    finish();
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void orientationChanged(int orientation) {
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return;
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info);
        orientation = (orientation + 45) / 90 * 90;
        int rotation = 0;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360;
        } else {  // back-facing camera
            rotation = (info.orientation + orientation) % 360;
        }

        if (mCamera != null) {
            Camera.Parameters params = mCamera.getParameters();
            params.setRotation(rotation);
            mCamera.setParameters(params);
        }
    }

    public void setCameraDisplayOrientation(Activity activity,
                                            int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
        mRotation = result;
    }


    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (mMachineLearningSemaphore.tryAcquire()) {

            if (machineLearningThread == null) {
                machineLearningThread = new MachineLearningThread();
                new Thread(machineLearningThread).start();
            }

            Camera.Parameters parameters = camera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;
            int format = parameters.getPreviewFormat();

            // Use the application context here because the machine learning thread's lifecycle
            // is connected to the application and not this activity
            machineLearningThread.post(bytes, width, height, format, mRotation, this,
                    this.getApplicationContext());
        }
    }

    @Override
    public void onClick(View view) {
        if (mCamera != null) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (parameters.getFlashMode().equals(Camera.Parameters.FLASH_MODE_TORCH)) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            } else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            }
            mCamera.setParameters(parameters);
            mCamera.startPreview();
        }

    }

    @Override
    public void onBackPressed() {
        if (!mSentResponse && mIsActivityActive) {
            mSentResponse = true;
            Intent intent = new Intent();
            setResult(RESULT_CANCELED, intent);
            finish();
        }
    }

    private void increment(HashMap<String, Integer> map, String key) {
        Integer currentValue = map.get(key);
        if (currentValue == null) {
            currentValue = 0;
        }

        map.put(key, currentValue + 1);
    }

    private void increment(HashMap<Expiry, Integer> map, Expiry key) {
        Integer currentValue = map.get(key);
        if (currentValue == null) {
            currentValue = 0;
        }

        map.put(key, currentValue + 1);
    }

    // Ugg there has to be a better way
    private String getNumberResult() {
        String result = null;
        int maxValue = 0;

        for (String number: numberResults.keySet()) {
            int value = 0;
            Integer count = numberResults.get(number);
            if (count != null) {
                value = count;
            }
            if (value > maxValue) {
                result = number;
                maxValue = value;
            }
        }

        return result;
    }

    private Expiry getExpiryResult() {
        Expiry result = null;
        int maxValue = 0;

        for (Expiry expiry: expiryResults.keySet()) {
            int value = 0;
            Integer count = expiryResults.get(expiry);
            if (count != null) {
                value = count;
            }
            if (value > maxValue) {
                result = expiry;
                maxValue = value;
            }
        }

        return result;
    }

    @Override
    public void onPrediction(final String number, final Expiry expiry, final Bitmap bitmap,
                             final List<DetectedBox> digitBoxes, final DetectedBox expiryBox) {

        mDebugImageView.setImageBitmap(ImageUtils.drawBoxesOnImage(bitmap, digitBoxes, expiryBox));
        if (!mSentResponse && mIsActivityActive) {

            if (number != null && firstResultMs == 0) {
                firstResultMs = SystemClock.uptimeMillis();
            }

            if (number != null) {
                increment(numberResults, number);
            }
            if (expiry != null) {
                increment(expiryResults, expiry);
            }

            long currentTime = SystemClock.uptimeMillis();

            if (firstResultMs != 0) {
                String numberResult = getNumberResult();
                Expiry expiryResult = getExpiryResult();
                TextView textView = findViewById(R.id.cardNumber);
                textView.setText(CreditCardUtils.format(numberResult));
                textView.setVisibility(View.VISIBLE);

                if (expiryResult != null) {
                    textView = findViewById(R.id.expiry);
                    textView.setText(expiryResult.format());
                    textView.setVisibility(View.VISIBLE);
                }
            }

            if (firstResultMs != 0 && (currentTime - firstResultMs) >= errorCorrectionDurationMs) {
                mSentResponse = true;
                Intent intent = new Intent();
                String numberResult = getNumberResult();
                Expiry expiryResult = getExpiryResult();
                String month = null;
                String year = null;
                if (expiryResult != null) {
                    month = Integer.toString(expiryResult.month);
                    year = Integer.toString(expiryResult.year);
                }

                CreditCard card = new CreditCard(numberResult, month, year);

                intent.putExtra(SCAN_RESULT, card);
                setResult(RESULT_OK, intent);
                finish();
            }
        }

        mMachineLearningSemaphore.release();
    }

    /** A basic Camera preview class */
    public class CameraPreview extends SurfaceView implements Camera.AutoFocusCallback, SurfaceHolder.Callback {
        private SurfaceHolder mHolder;

        public CameraPreview(Context context) {
            super(context);

            // Install a SurfaceHolder.Callback so we get notified when the
            // underlying surface is created and destroyed.
            mHolder = getHolder();
            mHolder.addCallback(this);
            // deprecated setting, but required on Android versions prior to 3.0
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

            Camera.Parameters params = mCamera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            params.setRecordingHint(true);
            mCamera.setParameters(params);
        }

        @Override
        public void onAutoFocus(boolean success, Camera camera) {

        }

        public void surfaceCreated(SurfaceHolder holder) {
            // The Surface has been created, now tell the camera where to draw the preview.
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
            } catch (IOException e) {
                Log.d("CameraCaptureActivity", "Error setting camera preview: " + e.getMessage());
            }
        }

        public void surfaceDestroyed(SurfaceHolder holder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.

            if (mHolder.getSurface() == null){
                // preview surface does not exist
                return;
            }

            // stop preview before making changes
            try {
                mCamera.stopPreview();
            } catch (Exception e){
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                mCamera.setPreviewDisplay(mHolder);
                mCamera.startPreview();
            } catch (Exception e){
                Log.d("CameraCaptureActivity", "Error starting camera preview: " + e.getMessage());
            }
        }
    }
}
