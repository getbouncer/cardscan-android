package com.getbouncer.cardscan;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;


// WARNING WARNING WARNING DO NOT USE still very much WIP
public class ScanCardStepUpActivity extends ScanBaseActivity implements View.OnClickListener {

    private static final int REQUEST_CODE = 43215;
    private static final String LAST4 = "last4";
    private static final String EXPIRY = "expiry";
    private static final String CARD_NETWORK = "card_network";

    private CreditCard.Network cardNetwork;
    private String expiry;
    private String last4;

    public static void start(Activity activity, String last4, String expiry,
                             CreditCard.Network network) {
        ScanBaseActivity.getMachineLearningThread().warmUp(activity.getApplicationContext());
        Intent intent = new Intent(activity, ScanCardStepUpActivity.class);
        intent.putExtra(LAST4, last4);
        intent.putExtra(EXPIRY, expiry);
        intent.putExtra(CARD_NETWORK, network);
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    public static boolean isStepUpResult(int requestCode) {
        return requestCode == REQUEST_CODE;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_step_up);

        this.last4 = getIntent().getStringExtra(LAST4);
        this.expiry = getIntent().getStringExtra(EXPIRY);
        this.cardNetwork = (CreditCard.Network) getIntent().getSerializableExtra(CARD_NETWORK);

        TextView last4AndExpiry = findViewById(R.id.last4AndExpiry);
        if (this.cardNetwork == CreditCard.Network.VISA) {
            last4AndExpiry.setCompoundDrawablesWithIntrinsicBounds(R.drawable.visa,
                    0, 0, 0);
        } else if (this.cardNetwork == CreditCard.Network.MASTERCARD) {
            last4AndExpiry.setCompoundDrawablesWithIntrinsicBounds(R.drawable.mastercard,
                    0, 0, 0);
        } else if (this.cardNetwork == CreditCard.Network.AMEX) {
            last4AndExpiry.setCompoundDrawablesWithIntrinsicBounds(R.drawable.amex,
                    0, 0, 0);
        } else if (this.cardNetwork == CreditCard.Network.DISCOVER) {
            last4AndExpiry.setCompoundDrawablesWithIntrinsicBounds(R.drawable.discover,
                    0, 0, 0);
        }

        // XXX FIXME
        if (this.expiry != null && this.expiry.length() > 0) {
            last4AndExpiry.setText(" " + this.last4 + "   Exp: " + this.expiry);
        } else {
            last4AndExpiry.setText(" " + this.last4);
        }

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

    @Override
    protected void onCardScanned(final CreditCard card) {
        super.onCardScanned(card);
    }

    private void startCameraAfterInitialization() {
        // XXX FIXME come up with a cleaner interface here
        // make sure to set the mIsPermissionCheckDone before calling startCamera
        mIsPermissionCheckDone = true;
        findViewById(R.id.flashlightButton).setVisibility(View.VISIBLE);
        findViewById(R.id.scanCardButton).setVisibility(View.GONE);
        findViewById(R.id.cardPreviewImage).setVisibility(View.GONE);
        OverlayWhite overlayWhite = findViewById(R.id.shadedBackground);
        overlayWhite.setColorIds(R.color.white_background_transparent, R.color.ios_green);
        startCamera();
    }
}
