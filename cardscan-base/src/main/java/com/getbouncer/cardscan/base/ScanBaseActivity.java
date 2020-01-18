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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.test.espresso.idling.CountingIdlingResource;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.io.File;
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
public abstract class ScanBaseActivity extends Activity implements Camera.PreviewCallback,
        View.OnClickListener, OnScanListener, OnObjectListener, OnCameraOpenListener {

    private Camera mCamera = null;
    private CameraPreview cameraPreview = null;
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
    private int mEnterCardManuallyId;
    private float mRoiCenterYRatio;
    private boolean mIsOcr = true;
    private boolean mDelayShowingExpiration = true;
    private byte[] machineLearningFrame = null;

    protected ScanStats scanStats;

    public static String IS_OCR = "is_ocr";
    public static String RESULT_FATAL_ERROR = "result_fatal_error";
    public static String RESULT_CAMERA_OPEN_ERROR = "result_camera_open_error";
    public static String RESULT_ENTER_CARD_MANUALLY_REASON = "result_enter_card_manually";
    public static String DELAY_SHOWING_EXPIRATION = "delay_showing_expiration";
    public boolean wasPermissionDenied = false;
    public String denyPermissionTitle;
    public String denyPermissionMessage;
    public String denyPermissionButton;

    // This is a hack to enable us to inject images to use for testing. There is probably a better
    // way to do this than using a static variable, but it works for now.
    static public TestingImageReaderInternal sTestingImageReader = null;
    private TestingImageReaderInternal mTestingImageReader = null;
    protected CountingIdlingResource mScanningIdleResource = null;

    // set when this activity posts to the machineLearningThread
    public long mPredictionStartMs = 0;
    // Child classes must set to ensure proper flashlight handling
    public boolean mIsPermissionCheckDone = false;
    protected boolean mShowNumberAndExpiryAsScanning = true;
    protected boolean postToMachineLearningThread = true;

    protected File objectDetectFile;

    public long errorCorrectionDurationMs = 1500;

    // This value sets the minimum width or height for any images we use for machine learning.
    // Make sure that it is equal to or larger than the width of the largest expected image
    // size. Our image pipeline will resize images to be memory efficient and will make sure that
    // the width or height of any of our images never goes below this value.
    public static int MIN_IMAGE_EDGE = 600;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        denyPermissionTitle = getString(R.string.card_scan_deny_permission_title);
        denyPermissionMessage = getString(R.string.card_scan_deny_permission_message);
        denyPermissionButton = getString(R.string.card_scan_deny_permission_button);

        // XXX FIXME move to dependency injection
        mTestingImageReader = sTestingImageReader;
        sTestingImageReader = null;

        this.scanStats = new ScanStats();

        mIsOcr = getIntent().getBooleanExtra(IS_OCR, true);
        mDelayShowingExpiration = getIntent().getBooleanExtra(DELAY_SHOWING_EXPIRATION, true);
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
            View view = findViewById(cardRectangleId);

            // convert from DP to pixels
            int radius = (int) (11 * Resources.getSystem().getDisplayMetrics().density);
            RectF rect = new RectF(
                    view.getLeft(),
                    view.getTop(),
                    view.getRight(),
                    view.getBottom());
            Overlay overlay = findViewById(overlayId);
            overlay.setRect(rect, radius);

            ScanBaseActivity.this.mRoiCenterYRatio =
                    (view.getTop() + (view.getHeight() * 0.5f)) / overlay.getHeight();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mIsPermissionCheckDone = true;
        } else {
            wasPermissionDenied = true;
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(this.denyPermissionMessage)
                    .setTitle(this.denyPermissionTitle);
            builder.setPositiveButton(this.denyPermissionButton, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                    // just let the user click on the back button manually
                    //onBackPressed();
                }
            });
            AlertDialog dialog = builder.create();
            dialog.show();
        }
    }

    private void setCameraParameters(Camera camera, Camera.Parameters parameters) {
        try {
            camera.setParameters(parameters);
        } catch (Exception | Error e) {
            e.printStackTrace();
        }
    }

    private void setCameraPreviewFrame() {
        int format = ImageFormat.NV21;
        Camera.Parameters parameters = mCamera.getParameters();
        parameters.setPreviewFormat(format);

        DisplayMetrics displayMetrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(displayMetrics);

        int displayWidth = Math.max(displayMetrics.heightPixels, displayMetrics.widthPixels);
        int displayHeight = Math.min(displayMetrics.heightPixels, displayMetrics.widthPixels);

        int height = MIN_IMAGE_EDGE;
        int width = displayWidth * height / displayHeight;

        Camera.Size previewSize = getOptimalPreviewSize(parameters.getSupportedPreviewSizes(),
                width, height);

        if (previewSize != null) {
            parameters.setPreviewSize(previewSize.width, previewSize.height);
        }
        setCameraParameters(mCamera, parameters);
    }

    @Override
    public void onCameraOpen(@Nullable Camera camera) {
        if (camera == null) {
            Intent intent = new Intent();
            intent.putExtra(RESULT_CAMERA_OPEN_ERROR, true);
            setResult(RESULT_CANCELED, intent);
            if (cameraPreview != null) {
                cameraPreview.getHolder().removeCallback(cameraPreview);
            }
            finish();
        } else if (!mIsActivityActive) {
            camera.release();
            if (cameraPreview != null) {
                cameraPreview.getHolder().removeCallback(cameraPreview);
            }
        } else {
            mCamera = camera;
            setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_BACK);
            setCameraPreviewFrame();
            // Create our Preview view and set it as the content of our activity.
            cameraPreview = new CameraPreview(this, this);
            FrameLayout preview = findViewById(mTextureId);
            preview.removeAllViews();
            preview.addView(cameraPreview);
        }
    }

    // https://stackoverflow.com/a/17804792
    private @Nullable Camera.Size getOptimalPreviewSize(List<Camera.Size> sizes, int w, int h) {
        final double ASPECT_TOLERANCE = 0.2;
        double targetRatio = (double) w / h;

        if (sizes==null) {
            return null;
        }

        Camera.Size optimalSize = null;

        // Find the smallest size that fits our tolerance and is at least as big as our target
        // height
        for (Camera.Size size : sizes) {
            double ratio = (double) size.width / size.height;
            if (Math.abs(ratio - targetRatio) <= ASPECT_TOLERANCE) {
                if (size.height >= h) {
                    optimalSize = size;
                }
            }
        }

        // Find the closest ratio that is still taller than our target height
        if (optimalSize == null) {
            double minDiffRatio = Double.MAX_VALUE;
            for (Camera.Size size : sizes) {
                double ratio = (double) size.width / size.height;
                double ratioDiff = Math.abs(ratio - targetRatio);
                if (size.height >= h && ratioDiff <= minDiffRatio) {
                    optimalSize = size;
                    minDiffRatio = ratioDiff;
                }
            }
        }

        return optimalSize;
    }


    protected void startCamera() {
        numberResults = new HashMap<>();
        expiryResults = new HashMap<>();
        firstResultMs = 0;

        try {
            if (mIsPermissionCheckDone) {
                mScanningIdleResource = IdleResourceManager.scanningIdleResource;
                IdleResourceManager.scanningIdleResource = null;
                if (mScanningIdleResource != null) {
                    mScanningIdleResource.increment();
                }

                CameraThread thread = new CameraThread();
                thread.start();
                thread.startCamera(this);
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
            mCamera.setPreviewCallbackWithBuffer(null);
            mCamera.release();
            mCamera = null;
        }

        if (cameraPreview != null) {
            cameraPreview.getHolder().removeCallback(cameraPreview);
            cameraPreview = null;
        }

        mIsActivityActive = false;
    }

    @Override
    protected void onResume() {
        super.onResume();

        mIsActivityActive = true;
        this.scanStats = new ScanStats();
        firstResultMs = 0;
        numberResults = new HashMap<>();
        expiryResults = new HashMap<>();
        mSentResponse = false;

        if (findViewById(mCardNumberId) != null) {
            findViewById(mCardNumberId).setVisibility(View.INVISIBLE);
        }
        if (findViewById(mExpiryId) != null) {
            findViewById(mExpiryId).setVisibility(View.INVISIBLE);
        }

        startCamera();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void setViewIds(int flashlightId, int cardRectangleId, int overlayId, int textureId,
                           int cardNumberId, int expiryId) {

        this.setViewIds(flashlightId, cardRectangleId, overlayId, textureId, cardNumberId, expiryId, View.NO_ID);

    }

    public void setViewIds(int flashlightId, int cardRectangleId, int overlayId, int textureId,
                    int cardNumberId, int expiryId, int enterCardManuallyId) {
        mFlashlightId = flashlightId;
        mTextureId = textureId;
        mCardNumberId = cardNumberId;
        mExpiryId = expiryId;
        mEnterCardManuallyId = enterCardManuallyId;
        View flashlight = findViewById(flashlightId);
        if (flashlight != null) {
            flashlight.setOnClickListener(this);
        }
        findViewById(cardRectangleId).getViewTreeObserver()
                .addOnGlobalLayoutListener(new MyGlobalListenerClass(cardRectangleId, overlayId));
    }

    public void setCameraDisplayOrientation(Activity activity, int cameraId) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
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
            result = (360 - result) % 360;  // compensate for the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        mCamera.setDisplayOrientation(result);
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
        if (postToMachineLearningThread && mMachineLearningSemaphore.tryAcquire()) {
            if (machineLearningFrame != null) {
                mCamera.addCallbackBuffer(machineLearningFrame);
            }
            machineLearningFrame = bytes;

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
                if (mIsOcr) {
                    mlThread.post(bytes, width, height, format, mRotation, this,
                            this.getApplicationContext(), mRoiCenterYRatio);
                } else {
                    mlThread.post(bytes, width, height, format, mRotation,this,
                            this.getApplicationContext(), mRoiCenterYRatio, objectDetectFile);
                }
            } else {
                Bitmap bm = mTestingImageReader.nextImage();
                if (mIsOcr) {
                    mlThread.post(bm, this, this.getApplicationContext());
                } else {
                    mlThread.post(bm, this, this.getApplicationContext(),
                            objectDetectFile);
                }
                if (bm == null) {
                    mTestingImageReader = null;
                }
            }
        } else {
            mCamera.addCallbackBuffer(bytes);
        }
    }

    @Override
    public void onClick(View view) {
        if (mEnterCardManuallyId == view.getId() && mEnterCardManuallyId != View.NO_ID) {
            onEnterCardManuallyPressed();
        }
        else if (mCamera != null && mFlashlightId == view.getId()) {
            Camera.Parameters parameters = mCamera.getParameters();
            if (Camera.Parameters.FLASH_MODE_TORCH.equals(parameters.getFlashMode())) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            } else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            }
            setCameraParameters(mCamera, parameters);
            mCamera.startPreview();
        }

    }

    protected void onEnterCardManuallyPressed() {
        Intent intent = new Intent();
        intent.putExtra(RESULT_ENTER_CARD_MANUALLY_REASON, true);
        setResult(RESULT_CANCELED, intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        if (!mSentResponse && mIsActivityActive) {
            this.scanStats.setSuccess(false);
            Api.scanStats(this, this.scanStats);

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
        setValueAnimated(textView, CreditCardUtils.formatNumberForDisplay(numberResult));

        boolean shouldShowExpiration = !mDelayShowingExpiration || duration >= (errorCorrectionDurationMs / 2);
        if (expiryResult != null && shouldShowExpiration) {
            textView = findViewById(mExpiryId);
            setValueAnimated(textView, expiryResult.format());
        }
    }

    @Override
    public void onFatalError() {
        Intent intent = new Intent();
        intent.putExtra(RESULT_FATAL_ERROR, true);
        setResult(RESULT_CANCELED, intent);
        finish();
    }

    @Override
    public void onPrediction(final String number, final Expiry expiry, final Bitmap bitmap,
                             final List<DetectedBox> digitBoxes, final DetectedBox expiryBox,
                             final Bitmap bitmapForObjectDetection, final Bitmap fullScreenBitmap) {

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
                Api.scanStats(this, this.scanStats);

                onCardScanned(numberResult, month, year);

                if (mScanningIdleResource != null) {
                    mScanningIdleResource.decrement();
                }
            }
        }

        mMachineLearningSemaphore.release();
    }

    @Override
    public void onObjectFatalError() {
        Log.d("ScanBaseActivity", "onObjectFatalError for object detection");
    }

    @Override
    public void onPrediction(Bitmap bm, List<DetectedSSDBox> boxes, int imageWidth,
                             int imageHeight, final Bitmap fullScreenBitmap) {
        if (!mSentResponse && mIsActivityActive) {
            // do something with the prediction

            // Note: This method is used by `AirBnB` to release the ML semaphore without performing
            // the voting algorithm. This is for the purpose of displaying "incorrect card" instead
            // of failing when the card does not match.
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

            Camera.Parameters params = mCamera.getParameters();
            List<String> focusModes = params.getSupportedFocusModes();
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            params.setRecordingHint(true);
            setCameraParameters(mCamera, params);
        }

        @Override
        public void onAutoFocus(boolean success, Camera camera) {

        }

        /**
         * The Surface has been created, now tell the camera where to draw the preview.
         */
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            try {
                mCamera.setPreviewDisplay(holder);
                mCamera.startPreview();
//                mCamera.setDisplayOrientation(90);
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
                int bufSize = w * h * ImageFormat.getBitsPerPixel(format) / 8;
                for (int i = 0; i < 3; i++) {
                    mCamera.addCallbackBuffer(new byte[bufSize]);
                }
                mCamera.setPreviewCallbackWithBuffer(mPreviewCallback);
                mCamera.startPreview();
            } catch (Exception e){
                Log.d("CameraCaptureActivity", "Error starting camera preview: " + e.getMessage());
            }
        }
    }
}
