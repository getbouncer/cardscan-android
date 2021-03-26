package com.getbouncer.cardscan.demo;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.getbouncer.cardscan.ui.CardScanActivity;
import com.getbouncer.cardscan.ui.CardScanActivityResult;
import com.getbouncer.cardscan.ui.CardScanActivityResultHandler;
import com.getbouncer.scan.framework.Config;
import com.getbouncer.scan.framework.Scan;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LaunchActivity extends AppCompatActivity implements CardScanActivityResultHandler {

    private static final String API_KEY = "Qm4Jo22kO0wVkSxsVu4VQJjevYMYFTVB";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        // Because this activity displays card numbers, disallow screenshots.
        getWindow().setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        );

        ((CheckBox) findViewById(R.id.enableDebugCheckbox))
                .setOnCheckedChangeListener((buttonView, isChecked) -> Config.setDebug(isChecked));

        findViewById(R.id.scanCardButton).setOnClickListener(v -> {
            final boolean enableNameExtraction =
                ((CheckBox) findViewById(R.id.enableNameExtractionCheckbox)).isChecked();
            final boolean enableExpiryExtraction =
                ((CheckBox) findViewById(R.id.enableExpiryExtractionCheckbox)).isChecked();
            final boolean enableEnterCardManually =
                ((CheckBox) findViewById(R.id.enableEnterCardManuallyCheckbox)).isChecked();

            CardScanActivity.start(
                /* activity */ LaunchActivity.this,
                /* apiKey */ API_KEY,
                /* enableEnterCardManually */ enableEnterCardManually,
                /* enableExpiryExtraction */ enableExpiryExtraction,
                /* enableNameExtraction */ enableNameExtraction
            );
        });

        if (Scan.INSTANCE.isDeviceArchitectureArm()) {
            ((TextView) findViewById(R.id.deviceArchitectureText))
                .setText(getString(
                    R.string.deviceArchitecture,
                    "arm: " + Scan.INSTANCE.getDeviceArchitecture()
                ));
        } else {
            ((TextView) findViewById(R.id.deviceArchitectureText))
                .setText(getString(
                    R.string.deviceArchitecture,
                    "NOT arm" + Scan.INSTANCE.getDeviceArchitecture()
                ));
        }

        findViewById(R.id.singleActivityDemo).setOnClickListener(v ->
                startActivity(new Intent(this, SingleActivityDemo.class))
        );

        CardScanActivity.warmUp(this, API_KEY, true);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (CardScanActivity.isScanResult(requestCode)) {
            CardScanActivity.parseScanResult(resultCode, data, this);
        }
    }

    @Override
    public void cardScanned(@Nullable String scanId, @NotNull CardScanActivityResult scanResult) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        StringBuilder message = new StringBuilder();
        message.append(scanResult.getPan());
        if (scanResult.getCardholderName() != null) {
            message.append("\nName: ");
            message.append(scanResult.getCardholderName());
        }
        if (scanResult.getExpiryMonth() != null && scanResult.getExpiryYear() != null) {
            message.append(
                    String.format("\nExpiry: %s/%s",
                    scanResult.getExpiryMonth(),
                    scanResult.getExpiryYear())
            );
        }
        if (scanResult.getErrorString() != null) {
            message.append("\nError: ");
            message.append(scanResult.getErrorString());
        }
        builder.setMessage(message);
        builder.show();
    }

    @Override
    public void enterManually(@Nullable String scanId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.enter_manually);
        builder.show();
    }

    @Override
    public void userCanceled(@Nullable String scanId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.scan_canceled);
        builder.show();
    }

    @Override
    public void cameraError(@Nullable String scanId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.camera_error);
        builder.show();
    }

    @Override
    public void analyzerFailure(@Nullable String scanId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.analyzer_error);
        builder.show();
    }

    @Override
    public void canceledUnknown(@Nullable String scanId) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.unknown_reason);
        builder.show();
    }
}
