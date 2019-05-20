package com.getbouncer.cardscan.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.getbouncer.cardscan.DetectedBox;
import com.getbouncer.cardscan.Expiry;
import com.getbouncer.cardscan.ImageUtils;
import com.getbouncer.cardscan.R;

import java.util.List;

public class ScanActivity extends ScanBaseActivity {
    private static final String TAG = "ScanActivity";
    private ImageView mDebugImageView;
    private boolean mInDebugMode = false;

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_capture);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.CAMERA}, 110);
            } else {
                mIsPermissionCheckDone = true;
            }
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
        }

        super.onPrediction(number, expiry, bitmap, digitBoxes, expiryBox);
    }

}
