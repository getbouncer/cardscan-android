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


import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;

import com.getbouncer.cardscan.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

public class ScanActivity extends Activity implements Camera.PictureCallback {

    private Camera mCamera = null;
    private Uri mPictureUri = null;
    private OrientationEventListener mOrientationEventListener;

    public static final String SCAN_RESULT = "creditCardJsonString";

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

        ImageButton captureButton = (ImageButton) findViewById(R.id.button_capture);
        final Camera.PictureCallback picture = this;
        captureButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (mCamera != null) {
                            mCamera.takePicture(null, null, picture);
                        }
                    }
                }
        );
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mCamera != null) {
            mCamera.release();
        }

        mOrientationEventListener.disable();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mOrientationEventListener.canDetectOrientation()) {
            mOrientationEventListener.enable();
        }

        try {
            mCamera = Camera.open();
            setPictureResolution(mCamera);
            setCameraDisplayOrientation(this, Camera.CameraInfo.CAMERA_FACING_BACK, mCamera);
            // Create our Preview view and set it as the content of our activity.
            CameraPreview cameraPreview = new CameraPreview(this);
            FrameLayout preview = (FrameLayout) findViewById(R.id.camera_preview);
            preview.addView(cameraPreview);
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

    private void setPictureResolution(Camera camera) {
        Camera.Parameters params = camera.getParameters();
        Camera.Size newSize = null;
        for (Camera.Size size : params.getSupportedPictureSizes()) {
            if (size.width <= 1024 && size.width <= 1024) {
                if (newSize == null) {
                    newSize = size;
                } else if (newSize.width < size.width && newSize.height < size.height) {
                    newSize = size;
                }
            }
        }

        if (newSize != null) {
            Log.d("CameraCaptureActivity", "setting picture resolution " +
                    newSize.width + "x" + newSize.height);
            params.setPictureSize(newSize.width, newSize.height);
            camera.setParameters(params);
            params = camera.getParameters();
            newSize = params.getPictureSize();
            Log.d("CameraCaptureActivity", "picture res " + newSize.width + "x" + newSize.height);
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

    public static void setCameraDisplayOrientation(Activity activity,
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
    }

    @Override
    public void onPictureTaken(byte[] data, Camera camera) {

        File dir = this.getDir("Pictures", Context.MODE_PRIVATE);
        File file = new File(dir, UUID.randomUUID().toString() + ".jpg");

        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
        } catch (FileNotFoundException e) {
            Log.d("CameraCaptureActivity", "File not found: " + e.getMessage());
        } catch (IOException e) {
            Log.d("CameraCaptureActivity", "Error accessing file: " + e.getMessage());
        }

        Intent i = new Intent();
        i.putExtra("picture_uri", file.toURI().toString());
        setResult(RESULT_OK, i);
        finish();
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
