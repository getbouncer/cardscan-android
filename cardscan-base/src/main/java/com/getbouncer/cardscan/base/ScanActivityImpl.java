package com.getbouncer.cardscan.base;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ScanActivityImpl extends ScanBaseActivity {
    private static final String TAG = "ScanActivityImpl";
    private ImageView mDebugImageView;
    private boolean mInDebugMode = false;
    private static long startTimeMs = 0;

    public static final String SCAN_CARD_TEXT = "scanCardText";
    public static final String POSITION_CARD_TEXT = "positionCardText";
    public static final String API_KEY = "apiKey";
    public static final String SHOW_ENTER_CARD_MANUALLY_BUTTON = "enterCardManuallyButton";
    public static final String CAMERA_PERMISSION_TITLE = "cameraPermissionTitle";
    public static final String CAMERA_PERMISSION_MESSAGE = "cameraPermissionMessage";

    public static final String RESULT_CARD_NUMBER = "cardNumber";
    public static final String RESULT_EXPIRY_MONTH = "expiryMonth";
    public static final String RESULT_EXPIRY_YEAR = "expiryYear";

    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bouncer_private_activity_scan_card);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        String scanCardText = getIntent().getStringExtra(SCAN_CARD_TEXT);
        if (!TextUtils.isEmpty(scanCardText)) {
            ((TextView) findViewById(R.id.scanCard)).setText(scanCardText);
        }

        String positionCardText = getIntent().getStringExtra(POSITION_CARD_TEXT);
        if (!TextUtils.isEmpty(positionCardText)) {
            ((TextView) findViewById(R.id.positionCard)).setText(positionCardText);
        }

        String apiKey = getIntent().getStringExtra(API_KEY);
        if (!TextUtils.isEmpty(apiKey)) {
            Api.apiKey = apiKey;
        }

        String cameraPermissionTitle = getIntent().getStringExtra(CAMERA_PERMISSION_TITLE);
        if (!TextUtils.isEmpty(cameraPermissionTitle)) {
            denyPermissionTitle = cameraPermissionTitle;
        }

        String cameraPermissionMessage = getIntent().getStringExtra(CAMERA_PERMISSION_MESSAGE);
        if (!TextUtils.isEmpty(cameraPermissionMessage)) {
            denyPermissionMessage = cameraPermissionMessage;
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

        findViewById(R.id.closeButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mDebugImageView = findViewById(R.id.debugImageView);
        mInDebugMode = getIntent().getBooleanExtra("debug", false);
        if (!mInDebugMode) {
            mDebugImageView.setVisibility(View.INVISIBLE);
        }

        boolean showEnterCardManuallyButton = getIntent().getBooleanExtra(SHOW_ENTER_CARD_MANUALLY_BUTTON, false);
        TextView enterCardManuallyButton = findViewById(R.id.enterCardManuallyButton);
        if (showEnterCardManuallyButton) {
            enterCardManuallyButton.setVisibility(View.VISIBLE);
        }

        setViewIds(R.id.flashlightButton, R.id.cardRectangle, R.id.shadedBackground, R.id.texture,
                R.id.cardNumber, R.id.expiry, R.id.enterCardManuallyButton);
    }

    @Override
    protected void onCardScanned(@NonNull String numberResult, @Nullable String month, @Nullable String year) {
        Intent intent = new Intent();
        intent.putExtra(RESULT_CARD_NUMBER, numberResult);
        intent.putExtra(RESULT_EXPIRY_MONTH, month);
        intent.putExtra(RESULT_EXPIRY_YEAR, year);
        setResult(RESULT_OK, intent);
        finish();
    }

    @Override
    public void onPrediction(
            @Nullable final String number,
            @Nullable final Expiry expiry,
            @NotNull final Bitmap ocrDetectionBitmap,
            @Nullable final List<DetectedBox> digitBoxes,
            @Nullable final DetectedBox expiryBox,
            @NotNull final Bitmap bitmapForObjectDetection,
            @Nullable final Bitmap screenDetectionBitmap
    ) {
        if (mInDebugMode) {
            mDebugImageView.setImageBitmap(ImageUtils.drawBoxesOnImage(ocrDetectionBitmap, digitBoxes,
                    expiryBox));
            Log.d(TAG, "Prediction (ms): " +
                    (SystemClock.uptimeMillis() - mPredictionStartMs));
            if (startTimeMs != 0) {
                Log.d(TAG, "time to first prediction: " +
                        (SystemClock.uptimeMillis() - startTimeMs));
                startTimeMs = 0;
            }
        }

        super.onPrediction(
                number,
                expiry,
                ocrDetectionBitmap,
                digitBoxes,
                expiryBox,
                bitmapForObjectDetection,
                screenDetectionBitmap
        );
    }

}
