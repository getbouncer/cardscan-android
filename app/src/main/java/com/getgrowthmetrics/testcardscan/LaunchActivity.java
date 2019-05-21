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

    private boolean isEnterCard = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        findViewById(R.id.scan_button).setOnClickListener(this);
        findViewById(R.id.scanCardDebug).setOnClickListener(this);
        findViewById(R.id.stepUp).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.scan_button) {
            startActivityForResult(new Intent(this, ScanActivity.class),
                    1234);
            isEnterCard = true;
        } else if (v.getId() == R.id.scanCardDebug) {
            Intent intent = new Intent(this, ScanActivity.class);
            intent.putExtra("debug", true);
            startActivityForResult(intent, 1234);
            isEnterCard = true;
        } else if (v.getId() == R.id.stepUp) {
            //startActivityForResult(new Intent(this, ScanCardStepUpActivity.class),
            //        1234);
            isEnterCard = false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1234) {
            if (resultCode == ScanActivity.RESULT_OK && data != null &&
                    data.hasExtra(ScanActivity.SCAN_RESULT)) {

                CreditCard scanResult = data.getParcelableExtra(ScanActivity.SCAN_RESULT);

                Intent intent;

                if (isEnterCard) {
                    intent = new Intent(this, EnterCard.class);
                    intent.putExtra("number", scanResult.number);

                    if (scanResult.expiryMonth != null && scanResult.expiryYear != null) {
                        intent.putExtra("expiryMonth", Integer.parseInt(scanResult.expiryMonth));
                        intent.putExtra("expiryYear", Integer.parseInt(scanResult.expiryYear));
                    }
                } else {
                    intent = new Intent(this, PlacingOrder.class);
                }

                startActivity(intent);
            } else if (resultCode == ScanActivity.RESULT_CANCELED) {
                Log.d(TAG, "The user pressed the back button");
            }
        }
    }
}
