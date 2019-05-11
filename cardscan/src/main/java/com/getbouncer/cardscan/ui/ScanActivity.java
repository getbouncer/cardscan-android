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
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.getbouncer.cardscan.R;

import java.io.IOException;
import java.util.concurrent.Semaphore;


public class ScanActivity extends Activity implements Camera.PreviewCallback {

    private Camera mCamera = null;
    private Uri mPictureUri = null;
    private OrientationEventListener mOrientationEventListener;
    private static MachineLearningThread machineLearningThread = null;
    private Semaphore mMachineLearningSemaphore = new Semaphore(1);
    private ImageView mDebugImageView;
    private int mRotation;

    public static final String SCAN_RESULT = "creditCardJsonString";
    private boolean mIsPermissionCheckDone = false;

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

        if (getIntent().hasExtra("picture_uri")) {
            String pictureUriString = getIntent().getStringExtra("picture_uri");
            if (!TextUtils.isEmpty(pictureUriString)) {
                mPictureUri = Uri.parse(pictureUriString);
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 110);
            } else {
                mIsPermissionCheckDone = true;
            }
        }

        mDebugImageView = findViewById(R.id.debugImageView);
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

        if (machineLearningThread != null) {
            machineLearningThread.mIsScanning = false;
        }

        mOrientationEventListener.disable();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }

        if (machineLearningThread != null) {
            machineLearningThread.mIsScanning = true;
        }

        try {
            if (mIsPermissionCheckDone) {
                mCamera = Camera.open();
                //setPictureResolution(mCamera);
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


            machineLearningThread.post(bytes, width, height, format, mRotation,
                    mMachineLearningSemaphore, this, mDebugImageView);
        }
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
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
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
