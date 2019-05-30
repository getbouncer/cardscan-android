package com.getgrowthmetrics.testcardscan;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.getbouncer.cardscan.CreditCard;
import com.getbouncer.cardscan.ScanActivity;

public class LaunchActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = "LaunchActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        findViewById(R.id.scan_button).setOnClickListener(this);
        findViewById(R.id.scanCardDebug).setOnClickListener(this);

        ScanActivity.warmUp(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.scan_button) {
            ScanActivity.start(this);
        } else if (v.getId() == R.id.scanCardDebug) {
            ScanActivity.startDebug(this);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (ScanActivity.isScanResult(requestCode)) {
            if (resultCode == ScanActivity.RESULT_OK && data != null &&
                    data.hasExtra(ScanActivity.SCAN_RESULT)) {

                CreditCard scanResult = data.getParcelableExtra(ScanActivity.SCAN_RESULT);

                Intent intent = new Intent(this, EnterCard.class);
                intent.putExtra("card", scanResult);
                startActivity(intent);
            } else if (resultCode == ScanActivity.RESULT_CANCELED) {
                Log.d(TAG, "The user pressed the back button");
            }
        }
    }
}
