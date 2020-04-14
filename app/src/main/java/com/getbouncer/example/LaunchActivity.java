package com.getbouncer.example;

import android.content.Intent;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import com.getbouncer.cardscan.CreditCard;
import com.getbouncer.cardscan.ScanActivity;


public class LaunchActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = "LaunchActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        // Because this activity displays card numbers, disallow screenshots.
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
        );

        findViewById(R.id.scan_button).setOnClickListener(this);
        findViewById(R.id.scanCardDebug).setOnClickListener(this);
        findViewById(R.id.scanCardAltText).setOnClickListener(this);
        findViewById(R.id.scan_video).setOnClickListener(this);
        ScanActivity.warmUp(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.scan_button) {
            ScanActivity.start(this, false, true);
        } else if (v.getId() == R.id.scanCardDebug) {
            ScanActivity.startDebug(this);
        } else if (v.getId() == R.id.scanCardAltText) {
            ScanActivity.start(this, "New Scan CreditCardUtils",
                    "Place your card here");
        } else if (v.getId() == R.id.scan_video) {
            ScanActivity.startDebug(this, new TestResourceImages(getResources()));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (ScanActivity.isScanResult(requestCode)) {
            if (resultCode == ScanActivity.RESULT_OK && data != null) {
                CreditCard scanResult = ScanActivity.creditCardFromResult(data);

                Intent intent = new Intent(this, EnterCard.class);
                intent.putExtra("card", scanResult);
                startActivity(intent);
            } else if (resultCode == ScanActivity.RESULT_CANCELED) {
                boolean fatalError = data.getBooleanExtra(ScanActivity.RESULT_FATAL_ERROR,
                        false);
                if (fatalError) {
                    Log.d(TAG, "fatal error");
                } else {
                    Log.d(TAG, "The user pressed the back button");
                }
            }
        }
    }
}
