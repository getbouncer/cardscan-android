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

package com.getbouncer.cardscan.base;


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
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.VisibleForTesting;
import android.support.test.espresso.idling.CountingIdlingResource;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Any classes that subclass this must:
 *
 * (1) set mIsPermissionCheckDone after the permission check is done, which should be sometime
 *     before "onResume" is called
 *
 * (2) Call setViewIds to set these resource IDs and initalize appropriate handlers
 */
public abstract class ScanBaseActivity extends Activity implements Camera.PreviewCallback, View.OnClickListener,
        OnScanListener {

    private Camera mCamera = null;
    private OrientationEventListener mOrientationEventListener;
    private static MachineLearningThread machineLearningThread = null;
    private Semaphore mMachineLearningSemaphore = new Semaphore(1);
    private int mRotation;
    private boolean mSentResponse = false;
    private boolean mIsActivityActive = false;
    private HashMap<String, Integer> numberResults = new HashMap<>();
    private HashMap<Expiry, Integer> expiryResults = new HashMap<>();
    private long firstResultMs = 0;
    private int mFlashlightId;
    private int mCardNumberId;
    private int mExpiryId;
    private int mTextureId;
    private float mRoiCenterYRatio;

    private ScanStats scanStats;

    // This is a hack to enable us to inject images to use for testing. There is probably a better
    // way to do this than using a static variable, but it works for now.
    static public TestingImageReaderInternal sTestingImageReader = null;
    private TestingImageReaderInternal mTestingImageReader = null;
    private CountingIdlingResource mScanningIdleResource = null;

    // set when this activity posts to the machineLearningThread
    public long mPredictionStartMs = 0;
    // Child classes must set to ensure proper flaslight handling
    public boolean mIsPermissionCheckDone = false;
    protected boolean mShowNumberAndExpiryAsScanning = true;

    public long errorCorrectionDurationMs = 1500;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // XXX FIXME move to dependency injection
        mTestingImageReader = sTestingImageReader;
        sTestingImageReader = null;
        mScanningIdleResource = IdleResourceManager.scanningIdleResource;
        IdleResourceManager.scanningIdleResource = null;

        if (mScanningIdleResource != null) {
            mScanningIdleResource.increment();
        }

        this.scanStats = new ScanStats();

        mOrientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                orientationChanged(orientation);
            }
        };
    }

    class MyGlobalListenerClass implements ViewTreeObserver.OnGlobalLayoutListener {
        private final int cardRectangleId;
        private final int overlayId;

        MyGlobalListenerClass(int cardRectangleId, int overlayId) {
            this.cardRectangleId = cardRectangleId;
            this.overlayId = overlayId;
        }

        @Override
        public void onGlobalLayout() {
            int[] xy = new int[2];
            View view = findViewById(cardRectangleId);
            view.getLocationOnScreen(xy);

            // convert from DP to pixels
            int radius = (int) (11 * Resources.getSystem().getDisplayMetrics().density);
            RectF rect = new RectF(xy[0], xy[1],
                    xy[0] + view.getWidth(),
                    xy[1] + view.getHeight());
            Overlay overlay = findViewById(overlayId);
            overlay.setCircle(rect, radius);

            ScanBaseActivity.this.mRoiCenterYRatio =
                    (xy[1] + view.getHeight() * 0.5f) / overlay.getHeight();
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

    protected void startCamera() {
        numberResults = new HashMap<>();
        expiryResults = new HashMap<>();
        firstResultMs = 0;
        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }

        try {
            if (mIsPermissionCheckDone) {
                mCamera = Camera.open();
                setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_BACK,
                        mCamera);
                // Create our Preview view and set it as the content of our activity.
                CameraPreview cameraPreview = new CameraPreview(this, this);
                FrameLayout preview = findViewById(mTextureId);
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
        startCamera();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void setViewIds(int flashlightId, int cardRectangleId, int overlayId, int textureId,
                    int cardNumberId, int expiryId) {
        mFlashlightId = flashlightId;
        mTextureId = textureId;
        mCardNumberId = cardNumberId;
        mExpiryId = expiryId;
        int mCardRectangleId = cardNumberId;
        findViewById(flashlightId).setOnClickListener(this);
        findViewById(cardRectangleId).getViewTreeObserver()
                .addOnGlobalLayoutListener(new MyGlobalListenerClass(cardRectangleId, overlayId));
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

    static public void warmUp(Context context) {
        getMachineLearningThread().warmUp(context);
    }

    static public MachineLearningThread getMachineLearningThread() {
        if (machineLearningThread == null) {
            machineLearningThread = new MachineLearningThread();
            new Thread(machineLearningThread).start();
        }

        return machineLearningThread;
    }

    @Override
    public void onPreviewFrame(byte[] bytes, Camera camera) {
        if (mMachineLearningSemaphore.tryAcquire()) {
            this.scanStats.incrementScans();

            MachineLearningThread mlThread = getMachineLearningThread();

            Camera.Parameters parameters = camera.getParameters();
            int width = parameters.getPreviewSize().width;
            int height = parameters.getPreviewSize().height;
            int format = parameters.getPreviewFormat();

            mPredictionStartMs = SystemClock.uptimeMillis();

            if (mTestingImageReader == null) {
                // Use the application context here because the machine learning thread's lifecycle
                // is connected to the application and not this activity
                mlThread.post(bytes, width, height, format, mRotation, this,
                        this.getApplicationContext(), mRoiCenterYRatio);
            } else {
                Bitmap bm = mTestingImageReader.nextImage();
                mlThread.post(bm, this, this.getApplicationContext());
                if (bm == null) {
                    mTestingImageReader = null;
                }
            }
        }
    }

    @Override
    public void onClick(View view) {
        if (mCamera != null && mFlashlightId == view.getId()) {
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
            this.scanStats.setSuccess(false);
            Api.fraudCheck(this, this.scanStats);

            mSentResponse = true;
            Intent intent = new Intent();
            setResult(RESULT_CANCELED, intent);
            finish();

            if (mScanningIdleResource != null) {
                mScanningIdleResource.decrement();
            }
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public void incrementNumber(String number) {
        Integer currentValue = numberResults.get(number);
        if (currentValue == null) {
            currentValue = 0;
        }

        numberResults.put(number, currentValue + 1);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public void incrementExpiry(Expiry expiry) {
        Integer currentValue = expiryResults.get(expiry);
        if (currentValue == null) {
            currentValue = 0;
        }

        expiryResults.put(expiry, currentValue + 1);
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public String getNumberResult() {
        // Ugg there has to be a better way
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

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    public Expiry getExpiryResult() {
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

    private void setValueAnimated(TextView textView, String value) {
        if (textView.getVisibility() != View.VISIBLE) {
            textView.setVisibility(View.VISIBLE);
            textView.setAlpha(0.0f);
            textView.animate().setDuration(400).alpha(1.0f);
        }
        textView.setText(value);
    }

    protected abstract void onCardScanned(String numberResult, String month, String year);

    protected void setNumberAndExpiryAnimated(long duration) {
        String numberResult = getNumberResult();
        Expiry expiryResult = getExpiryResult();
        TextView textView = findViewById(mCardNumberId);
        setValueAnimated(textView, CreditCardUtils.format(numberResult));

        if (expiryResult != null && duration >= (errorCorrectionDurationMs / 2)) {
            textView = findViewById(mExpiryId);
            setValueAnimated(textView, expiryResult.format());
        }
    }

    @Override
    public void onPrediction(final String number, final Expiry expiry, final Bitmap bitmap,
                             final List<DetectedBox> digitBoxes, final DetectedBox expiryBox) {

        if (!mSentResponse && mIsActivityActive) {

            if (number != null && firstResultMs == 0) {
                firstResultMs = SystemClock.uptimeMillis();
            }

            if (number != null) {
                incrementNumber(number);
            }
            if (expiry != null) {
                incrementExpiry(expiry);
            }

            long duration = SystemClock.uptimeMillis() - firstResultMs;
            if (firstResultMs != 0 && mShowNumberAndExpiryAsScanning) {
                setNumberAndExpiryAnimated(duration);
            }

            if (firstResultMs != 0 && duration >= errorCorrectionDurationMs) {
                mSentResponse = true;
                String numberResult = getNumberResult();
                Expiry expiryResult = getExpiryResult();
                String month = null;
                String year = null;
                if (expiryResult != null) {
                    month = Integer.toString(expiryResult.month);
                    year = Integer.toString(expiryResult.year);
                }

                this.scanStats.setSuccess(true);
                Api.fraudCheck(this, this.scanStats);

                onCardScanned(numberResult, month, year);

                if (mScanningIdleResource != null) {
                    mScanningIdleResource.decrement();
                }
            }
        }

        mMachineLearningSemaphore.release();
    }

    /** A basic Camera preview class */
    public class CameraPreview extends SurfaceView implements Camera.AutoFocusCallback, SurfaceHolder.Callback {
        private SurfaceHolder mHolder;
        private Camera.PreviewCallback mPreviewCallback;

        public CameraPreview(Context context, Camera.PreviewCallback previewCallback) {
            super(context);

            mPreviewCallback = previewCallback;

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
                mCamera.setPreviewCallback(mPreviewCallback);
                mCamera.startPreview();
            } catch (Exception e){
                Log.d("CameraCaptureActivity", "Error starting camera preview: " + e.getMessage());
            }
        }
    }
}
