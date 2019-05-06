package com.example.testandroidocr;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import com.getbouncer.cardscan.CreditCard;
import com.getbouncer.cardscan.ui.ScanActivity;

public class LaunchActivity extends AppCompatActivity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        findViewById(R.id.scan_button).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.scan_button) {
            startActivityForResult(new Intent(this, ScanActivity.class), 1234);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1234) {
            if ( resultCode == ScanActivity.RESULT_OK && data != null &&
                    data.hasExtra(ScanActivity.SCAN_RESULT)) {

                String resultString = data.getStringExtra(ScanActivity.SCAN_RESULT);
                CreditCard scanResult = new CreditCard(resultString);

                Intent intent = new Intent(this, EnterCard.class);
                intent.putExtra("number", scanResult.number);

                if (scanResult.expiryMonth != null) {
                    intent.putExtra("expiryMonth", Integer.parseInt(scanResult.expiryMonth));
                    intent.putExtra("expiryYear", Integer.parseInt(scanResult.expiryYear));
                }

                startActivity(intent);
            }
        }
    }
}
