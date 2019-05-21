package com.getbouncer.cardscan.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;

import com.getbouncer.cardscan.R;

public class ScanCardStepUpActivity extends ScanBaseActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_up);

        findViewById(R.id.scanCardButton).setOnClickListener(this);
        setViewIds(R.id.flashlightButton, R.id.cardRectangle, R.id.shadedBackground, R.id.texture,
                R.id.cardNumber, R.id.expiry);
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

    private void startCameraAfterInitialization() {
        mIsPermissionCheckDone = true;
        findViewById(R.id.scanCardButton).setVisibility(View.GONE);
        OverlayWhite overlayWhite = findViewById(R.id.shadedBackground);
        overlayWhite.setColorIds(R.color.white_background_transparent, R.color.dark_gray);
        startCamera();
    }
}
