package com.getbouncer.cardscan.demo;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.getbouncer.cardscan.ui.CardScanSheet;
import com.getbouncer.cardscan.ui.CardScanSheetResult;
import com.getbouncer.cardscan.ui.ScannedCard;
import com.getbouncer.scan.framework.Config;
import com.getbouncer.scan.framework.Scan;
import com.getbouncer.scan.ui.CancellationReason;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Unit;

public class LaunchActivity extends AppCompatActivity {

    private static final String API_KEY = "qOJ_fF-WLDMbG05iBq5wvwiTNTmM2qIn";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launch);

        final CardScanSheet sheet = CardScanSheet.create(this, API_KEY, this::handleScanResult);

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

            sheet.present(
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

        CardScanSheet.prepareScan(this, API_KEY, true, () -> null);
    }

    private Unit handleScanResult(final CardScanSheetResult result) {
        if (result instanceof CardScanSheetResult.Completed) {
            cardScanned(((CardScanSheetResult.Completed) result).getScannedCard());
        } else if (result instanceof CardScanSheetResult.Canceled) {
            userCanceled(((CardScanSheetResult.Canceled) result).getReason());
        } else if (result instanceof CardScanSheetResult.Failed) {
            analyzerFailure(((CardScanSheetResult.Failed) result).getError());
        }

        return Unit.INSTANCE;
    }

    private void cardScanned(@NotNull final ScannedCard scanResult) {
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

    private void userCanceled(@NotNull final CancellationReason reason) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        if (reason instanceof CancellationReason.Back) {
            builder.setMessage(R.string.user_pressed_back);
        } else if (reason instanceof CancellationReason.Closed) {
            builder.setMessage(R.string.scan_canceled);
        } else if (reason instanceof CancellationReason.CameraPermissionDenied) {
            builder.setMessage(R.string.permission_denied);
        } else if (reason instanceof CancellationReason.UserCannotScan) {
            builder.setMessage(R.string.enter_manually);
        }
        builder.show();
    }

    private void analyzerFailure(@NotNull final Throwable reason) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(reason.getMessage());
        builder.show();
    }
}
