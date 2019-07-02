package com.getbouncer.example;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.getbouncer.cardscan.CreditCard;
import com.getbouncer.cardscan.ScanActivity;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class LaunchActivity extends AppCompatActivity implements View.OnClickListener {

    public static final String TAG = "LaunchActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        findViewById(R.id.scan_button).setOnClickListener(this);
        findViewById(R.id.scanCardDebug).setOnClickListener(this);
        findViewById(R.id.scanCardAltText).setOnClickListener(this);
        findViewById(R.id.scan_video).setOnClickListener(this);

        ScanActivity.warmUp(this);
    }

    private String copyResourceToFile(int resourceId) {
        File outFileName = new File(getFilesDir(), "test_video.mov");

        if (outFileName.exists()) {
            return outFileName.getAbsolutePath();
        }

        FileDescriptor fd = getResources().openRawResourceFd(resourceId).getFileDescriptor();
        FileInputStream inputStream = new FileInputStream(fd);
        try {
            FileOutputStream out = new FileOutputStream(outFileName);

            byte[] data = new byte[4096];
            int n;
            do {
                n = inputStream.read(data);
                if (n > 0) {
                    out.write(data, 0, n);
                }
            } while (n > 0);
            out.flush();
            out.close();

            return outFileName.getAbsolutePath();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            outFileName.delete();
            return null;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.scan_button) {
            ScanActivity.start(this);
        } else if (v.getId() == R.id.scanCardDebug) {
            ScanActivity.startDebug(this);
        } else if (v.getId() == R.id.scanCardAltText) {
            ScanActivity.start(this, "New Scan Card",
                    "Place your card here");
        } else if (v.getId() == R.id.scan_video) {
            AssetFileDescriptor fd = getResources().openRawResourceFd(R.raw.card_video);
            ScanActivity.startDebug(this, new TestResourceImages(fd));
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
                Log.d(TAG, "The user pressed the back button");
            }
        }
    }
}
