package com.getbouncer.cardscan;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import java.util.List;

public class ScanActivity extends ScanBaseActivity {
    private static final String TAG = "ScanActivity";
    private ImageView mDebugImageView;
    private boolean mInDebugMode = false;
    private static long startTimeMs = 0;

    private static final int REQUEST_CODE = 51234;

    public static void start(Activity activity) {
        ScanBaseActivity.getMachineLearningThread().warmUp(activity.getApplicationContext());
        activity.startActivityForResult(new Intent(activity, ScanActivity.class), REQUEST_CODE);
    }

    public static void warmUp(Activity activity) {
        ScanBaseActivity.getMachineLearningThread().warmUp(activity.getApplicationContext());
    }

    public static void startDebug(Activity activity) {
        ScanBaseActivity.getMachineLearningThread().warmUp(activity.getApplicationContext());
        startTimeMs = SystemClock.uptimeMillis();
        Intent intent = new Intent(activity, ScanActivity.class);
        intent.putExtra("debug", true);
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    public static boolean isScanResult(int requestCode) {
        return requestCode == REQUEST_CODE;
    }

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_capture);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 110);
            } else {
                mIsPermissionCheckDone = true;
            }
        } else {
            // no permission checks
            mIsPermissionCheckDone = true;
        }

        mDebugImageView = findViewById(R.id.debugImageView);
        mInDebugMode = getIntent().getBooleanExtra("debug", false);
        if (!mInDebugMode) {
            mDebugImageView.setVisibility(View.INVISIBLE);
        }
        setViewIds(R.id.flashlightButton, R.id.cardRectangle, R.id.shadedBackground, R.id.texture,
                R.id.cardNumber, R.id.expiry);
    }

    @Override
    public void onPrediction(final String number, final Expiry expiry, final Bitmap bitmap,
                             final List<DetectedBox> digitBoxes, final DetectedBox expiryBox) {

        if (mInDebugMode) {
            mDebugImageView.setImageBitmap(ImageUtils.drawBoxesOnImage(bitmap, digitBoxes,
                    expiryBox));
            Log.d(TAG, "Prediction (ms): " +
                    (SystemClock.uptimeMillis() - mPredictionStartMs));
            if (startTimeMs != 0) {
                Log.d(TAG, "time to first prediction: " +
                        (SystemClock.uptimeMillis() - startTimeMs));
                startTimeMs = 0;
            }
        }

        super.onPrediction(number, expiry, bitmap, digitBoxes, expiryBox);
    }

}
