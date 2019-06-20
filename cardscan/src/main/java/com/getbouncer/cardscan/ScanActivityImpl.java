package com.getbouncer.cardscan;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.getbouncer.cardscan.base.DetectedBox;
import com.getbouncer.cardscan.base.Expiry;
import com.getbouncer.cardscan.base.ImageUtils;
import com.getbouncer.cardscan.base.ScanBaseActivity;

import java.util.List;

/**
 * The ScanActivity class provides the main interface to the scanning functionality. To use this
 * activity, call the {@link ScanActivity#start(Activity)} method and override
 * {@link Activity#onActivityResult(int, int, Intent)} to get the result of the scan.
 */
class ScanActivityImpl extends ScanBaseActivity {
    private static final String TAG = "ScanActivityImpl";
    private ImageView mDebugImageView;
    private boolean mInDebugMode = false;
    private static long startTimeMs = 0;

    static final String SCAN_CARD_TEXT = "scanCardText";
    static final String POSITION_CARD_TEXT = "positionCardText";

    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_card);

        String scanCardText = getIntent().getStringExtra(SCAN_CARD_TEXT);
        if (!TextUtils.isEmpty(scanCardText)) {
            ((TextView) findViewById(R.id.scanCard)).setText(scanCardText);
        }

        String positionCardText = getIntent().getStringExtra(POSITION_CARD_TEXT);
        if (!TextUtils.isEmpty(positionCardText)) {
            ((TextView) findViewById(R.id.positionCard)).setText(positionCardText);
        }


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
    protected void onCardScanned(String numberResult, String month, String year) {
        CreditCard card = new CreditCard(numberResult, month, year);
        Intent intent = new Intent();
        intent.putExtra(SCAN_RESULT, card);
        setResult(RESULT_OK, intent);
        finish();
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
