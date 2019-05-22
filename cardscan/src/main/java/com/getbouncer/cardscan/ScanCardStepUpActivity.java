package com.getbouncer.cardscan;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

// WARNING WARNING WARNING DO NOT USE still very much WIP
public class ScanCardStepUpActivity extends ScanBaseActivity implements View.OnClickListener {

    private static final int REQUEST_CODE = 43215;

    public static void start(Activity activity) {
        ScanBaseActivity.getMachineLearningThread().warmUp(activity.getApplicationContext());
        activity.startActivityForResult(new Intent(activity, ScanCardStepUpActivity.class),
                REQUEST_CODE);
    }

    public static void warmUp(Activity activity) {
        ScanBaseActivity.getMachineLearningThread().warmUp(activity.getApplicationContext());
    }

    public static boolean isStepUpResult(int requestCode) {
        return requestCode == REQUEST_CODE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_up);

        findViewById(R.id.scanCardButton).setOnClickListener(this);
        setViewIds(R.id.flashlightButton, R.id.cardRectangle, R.id.shadedBackground, R.id.texture,
                R.id.cardNumber, R.id.expiry);
        mShowNumberAndExpiryAsScanning = false;
        errorCorrectionDurationMs = 0;
    }

    @Override
    public void onClick(View view) {
        super.onClick(view);

        if (view.getId() == R.id.scanCardButton) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, 110);
                } else {
                   startCameraAfterInitialization();
                }
            } else {
                // no permission check
                startCameraAfterInitialization();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {

        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCameraAfterInitialization();
        }
    }

    @Override
    protected void onCardScanned(final CreditCard card) {
        // override for notifications after a successful scan
        findViewById(R.id.checkImage).setVisibility(View.VISIBLE);
        findViewById(R.id.checkImage).setAlpha(0.0f);
        findViewById(R.id.checkImage).animate().setDuration(400).alpha(1.0f);
        setNumberAndExpiryAnimated();

        // XXX FIXME there has to be a better way
        Thread thread = new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                }

                runOnUiThread(new Runnable() {
                    public void run() {
                        ScanCardStepUpActivity.super.onCardScanned(card);
                    }
                });
            }
        };
        thread.start();
    }


    private void startCameraAfterInitialization() {
        // XXX FIXME come up with a cleaner interface here
        // make sure to set the mIsPermissionCheckDone before calling startCamera
        mIsPermissionCheckDone = true;
        findViewById(R.id.flashlightButton).setVisibility(View.VISIBLE);
        findViewById(R.id.scanCardButton).setVisibility(View.GONE);
        OverlayWhite overlayWhite = findViewById(R.id.shadedBackground);
        overlayWhite.setColorIds(R.color.white_background_transparent, R.color.ios_green);
        startCamera();
    }
}
