package com.getbouncer.cardscan.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getbouncer.cardscan.ui.CardScanFlow;
import com.getbouncer.cardscan.ui.result.MainLoopNameExpiryState;
import com.getbouncer.cardscan.ui.result.MainLoopOcrState;
import com.getbouncer.scan.camera.CameraAdapter;
import com.getbouncer.scan.camera.CameraErrorListener;
import com.getbouncer.scan.camera.camera1.Camera1Adapter;
import com.getbouncer.scan.framework.AggregateResultListener;
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener;
import com.getbouncer.scan.framework.Config;
import com.getbouncer.scan.framework.Stats;
import com.getbouncer.scan.framework.api.BouncerApi;
import com.getbouncer.scan.framework.api.dto.ScanStatistics;
import com.getbouncer.scan.framework.interop.BlockingAggregateResultListener;
import com.getbouncer.scan.framework.interop.EmptyJavaContinuation;
import com.getbouncer.scan.framework.util.AppDetails;
import com.getbouncer.scan.framework.util.Device;
import com.getbouncer.scan.payment.card.PanFormatterKt;
import com.getbouncer.scan.payment.card.PaymentCardUtils;
import com.getbouncer.scan.payment.ml.ExpiryDetect;
import com.getbouncer.scan.ui.ViewFinderBackground;
import com.getbouncer.scan.ui.util.ViewExtensionsKt;

import java.util.Locale;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

public class SingleActivityDemo extends AppCompatActivity implements CameraErrorListener,
        AnalyzerLoopErrorListener, CoroutineScope {

    private enum State {
        NOT_FOUND,
        FOUND,
        CORRECT
    }

    private static final int PERMISSION_REQUEST_CODE = 1200;
    private static final Size MINIMUM_RESOLUTION = new Size(1280, 720);

    private Button scanCardButton;
    private View scanView;

    private FrameLayout cameraPreview;

    private FrameLayout viewFinderWindow;
    private ViewFinderBackground viewFinderBackground;
    private ImageView viewFinderBorder;

    private ImageView flashButtonView;

    private TextView cardPanTextView;
    private TextView cardNameTextView;

    private CameraAdapter<Bitmap> cameraAdapter;

    private CardScanFlow cardScanFlow;

    private State scanState = State.NOT_FOUND;

    /**
     * CardScan uses kotlin coroutines to run multiple analyzers in parallel for maximum image
     * throughput. This coroutine context binds the coroutines to this activity, so that if this
     * activity is terminated, all coroutines are terminated and there is no work leak.
     *
     * Additionally, this specifies which threads the coroutines will run on. Normally, the default
     * dispatchers should be used so that coroutines run on threads bound by the number of CPU
     * cores.
     */
    @NotNull
    @Override
    public CoroutineContext getCoroutineContext() {
        return Dispatchers.getDefault();
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_demo);

        scanCardButton = findViewById(R.id.scanCardButton);
        scanView = findViewById(R.id.scanView);

        scanCardButton.setOnClickListener(v -> {
            scanCardButton.setVisibility(View.GONE);
            scanView.setVisibility(View.VISIBLE);

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission();
            } else {
                startScan();
            }
        });

        cameraPreview = findViewById(R.id.cameraPreviewHolder);
        viewFinderWindow = findViewById(R.id.viewFinderWindow);
        viewFinderBackground = findViewById(R.id.viewFinderBackground);
        viewFinderBorder = findViewById(R.id.viewFinderBorder);

        flashButtonView = findViewById(R.id.flashButtonView);
        ImageView closeButtonView = findViewById(R.id.closeButtonView);

        cardPanTextView = findViewById(R.id.cardPanTextView);
        cardNameTextView = findViewById(R.id.cardNameTextView);

        closeButtonView.setOnClickListener(v -> userCancelScan());
        flashButtonView.setOnClickListener(v -> setFlashlightState(!cameraAdapter.isTorchOn()));

        // Allow the user to set the focus of the camera by tapping on the view finder.
        viewFinderWindow.setOnTouchListener((v, event) -> {
            cameraAdapter.setFocus(new PointF(
                event.getX() + viewFinderWindow.getLeft(), 
                event.getY() + viewFinderWindow.getTop())
            );
            return true;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cardScanFlow != null) {
            cardScanFlow.cancelFlow();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        setFlashlightState(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setStateNotFound();
    }

    /**
     * Request permission to use the camera.
     */
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this,
            new String[] { Manifest.permission.CAMERA },
            PERMISSION_REQUEST_CODE
        );
    }

    /**
     * Handle permission status changes. If the camera permission has been granted, start it. If
     * not, show a dialog.
     */
    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        @NotNull String[] permissions,
        @NotNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScan();
            } else {
                showPermissionDeniedDialog();
            }
        }
    }

    /**
     * Show an explanation dialog for why we are requesting camera permissions.
     */
    private void showPermissionDeniedDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(R.string.bouncer_camera_permission_denied_message)
            .setPositiveButton(
                R.string.bouncer_camera_permission_denied_ok,
                (dialog, which) -> requestCameraPermission()
            )
            .setNegativeButton(
                R.string.bouncer_camera_permission_denied_cancel,
                (dialog, which) -> startScan()
            )
            .show();
    }

    /**
     * Start the scanning flow.
     */
    private void startScan() {
        // ensure the cameraPreview view has rendered.
        cameraPreview.post(() -> {
            // Track scan statistics for health check
            Stats.INSTANCE.startScan(new EmptyJavaContinuation<>());

            // Tell the background where to draw a hole for the viewfinder window
            viewFinderBackground.setViewFinderRect(ViewExtensionsKt.asRect(viewFinderWindow));

            // Create a camera adapter and bind it to this activity.
            cameraAdapter = new Camera1Adapter(this, cameraPreview, MINIMUM_RESOLUTION, this, this);
            cameraAdapter.bindToLifecycle(this);
            cameraAdapter.withFlashSupport(supported -> {
                flashButtonView.setVisibility(supported ? View.VISIBLE : View.INVISIBLE);
                return Unit.INSTANCE;
            });

            // Create and start a CardScanFlow which will handle the business logic of the scan
            cardScanFlow = new CardScanFlow(true, true, aggregateResultListener, this);
            cardScanFlow.startFlow(
                this,
                cameraAdapter.getImageStream(),
                new Size(cameraPreview.getWidth(), cameraPreview.getHeight()),
                ViewExtensionsKt.asRect(viewFinderWindow),
                this,
                this
            );
        });
    }

    /**
     * Turn the flashlight on or off.
     */
    private void setFlashlightState(boolean on) {
        if (cameraAdapter != null) {
            cameraAdapter.setTorchState(on);

            if (cameraAdapter.isTorchOn()) {
                flashButtonView.setImageResource(R.drawable.bouncer_flash_on_dark);
            } else {
                flashButtonView.setImageResource(R.drawable.bouncer_flash_off_dark);
            }
        }
    }

    /**
     * Cancel scanning due to analyzer failure
     */
    private void analyzerFailureCancelScan(@Nullable final Throwable cause) {
        Log.e(Config.getLogTag(), "Canceling scan due to analyzer error", cause);
        new AlertDialog.Builder(this)
            .setMessage("Analyzer failure")
            .show();
        closeScanner();
    }

    /**
     * Cancel scanning due to a camera error.
     */
    private void cameraErrorCancelScan(@Nullable final Throwable cause) {
        Log.e(Config.getLogTag(), "Canceling scan due to camera error", cause);
        new AlertDialog.Builder(this)
            .setMessage("Camera error")
            .show();
        closeScanner();
    }

    /**
     * The scan has been cancelled by the user.
     */
    private void userCancelScan() {
        new AlertDialog.Builder(this)
            .setMessage("Scan Canceled by user")
            .show();
        closeScanner();
    }

    /**
     * Show the completed scan results
     */
    private void completeScan(
        @Nullable String expiryMonth,
        @Nullable String expiryYear,
        @Nullable String cardNumber,
        @Nullable String issuer,
        @Nullable String name,
        @Nullable String error
    ) {
        new AlertDialog.Builder(this)
            .setMessage(String.format(
                Locale.getDefault(),
                "%s\n%s\n%s/%s\n%s\n%s",
                cardNumber,
                issuer,
                expiryMonth,
                expiryYear,
                name,
                error
            ))
            .show();
        closeScanner();
    }

    /**
     * Close the scanner.
     */
    private void closeScanner() {
        Stats.INSTANCE.finishScan(new EmptyJavaContinuation<>());
        setFlashlightState(false);
        scanCardButton.setVisibility(View.VISIBLE);
        scanView.setVisibility(View.GONE);
        setStateNotFound();
        cameraAdapter.unbindFromLifecycle(this);
        if (cardScanFlow != null) {
            cardScanFlow.cancelFlow();
        }
        BouncerApi.uploadScanStats(
            this,
            Stats.INSTANCE.getInstanceId(),
            Stats.INSTANCE.getScanId(),
            Device.fromContext(this),
            AppDetails.fromContext(this),
            ScanStatistics.fromStats()
        );
    }

    @Override
    public void onCameraOpenError(@Nullable Throwable cause) {
        cameraErrorCancelScan(cause);
    }

    @Override
    public void onCameraAccessError(@Nullable Throwable cause) {
        cameraErrorCancelScan(cause);
    }

    @Override
    public void onCameraUnsupportedError(@Nullable Throwable cause) {
        cameraErrorCancelScan(cause);
    }

    @Override
    public boolean onAnalyzerFailure(@NotNull Throwable t) {
        analyzerFailureCancelScan(t);
        return true;
    }

    @Override
    public boolean onResultFailure(@NotNull Throwable t) {
        analyzerFailureCancelScan(t);
        return true;
    }

    private AggregateResultListener<
            CardScanFlow.InterimResult,
            CardScanFlow.FinalResult> aggregateResultListener =
            new BlockingAggregateResultListener<
                    CardScanFlow.InterimResult,
                    CardScanFlow.FinalResult>() {

        /**
         * An interim result has been received from the scan, the scan is still running. Update your
         * UI as necessary here to display the progress of the scan.
         */
        @Override
        public void onInterimResultBlocking(CardScanFlow.InterimResult interimResult) {
            new Handler(getMainLooper()).post(() -> {
                final MainLoopOcrState mainLoopOcrState = interimResult.getOcrState();
                final MainLoopNameExpiryState mainLoopNameExpiryState =
                        interimResult.getNameExpiryState();

                if (mainLoopOcrState instanceof MainLoopOcrState.Initial) {
                    // In initial state, show no card found
                    setStateNotFound();

                } else if (mainLoopOcrState instanceof MainLoopOcrState.OcrRunning) {
                    // If OCR is running and a valid card number is visible, display it
                    final MainLoopOcrState.OcrRunning state =
                        (MainLoopOcrState.OcrRunning) mainLoopOcrState;
                    final String pan = state.getMostLikelyPan();
                    if (pan != null) {
                        cardPanTextView.setText(PanFormatterKt.formatPan(pan));
                        ViewExtensionsKt.fadeIn(cardPanTextView, null);
                    }
                    setStateFound();

                } else if (mainLoopNameExpiryState instanceof
                        MainLoopNameExpiryState.NameAndExpiryRunning) {
                    // If name and expiry are running and a valid name is found, display it
                    final MainLoopNameExpiryState.NameAndExpiryRunning state =
                            (MainLoopNameExpiryState.NameAndExpiryRunning) mainLoopNameExpiryState;
                    final String name = state.getMostLikelyName();
                    if (name != null) {
                        cardNameTextView.setText(name);
                        ViewExtensionsKt.fadeIn(cardNameTextView, null);
                    }
                    setStateFound();

                } else if (mainLoopOcrState instanceof MainLoopOcrState.Finished) {
                    // If ocr has finished, name and expiry extraction will start
                    setStateFound();

                } else if (mainLoopNameExpiryState instanceof MainLoopNameExpiryState.Finished) {
                    // If name and expiry finished, display done
                    setStateCorrect();
                }
            });
        }

        /**
         * The scan has completed and the final result is available. Close the scanner and make use
         * of the final result.
         */
        @Override
        public void onResultBlocking(CardScanFlow.FinalResult result) {
            final ExpiryDetect.Expiry expiry = result.getExpiry();

            new Handler(getMainLooper()).post(() -> {
                // Only show the expiry dates that are not expired
                completeScan(
                    expiry != null && expiry.isValidExpiry() ? expiry.getMonth() : null,
                    expiry != null && expiry.isValidExpiry() ? expiry.getYear() : null,
                    result.getPan(),
                    PaymentCardUtils.getCardIssuer(result.getPan()).getDisplayName(),
                    result.getName(),
                    result.getErrorString()
                );
            });
        }

        /**
         * The scan was reset (usually because the activity was backgrounded). Reset the UI.
         */
        @Override
        public void onResetBlocking() {
            new Handler(getMainLooper()).post(() -> setStateNotFound());
        }
    };

    /**
     * Display a blue border tracing the outline of the card to indicate that the card is identified
     * and scanning is running.
     */
    private void setStateFound() {
        if (scanState == State.FOUND) return;
        ViewExtensionsKt.startAnimation(viewFinderBorder,
            R.drawable.bouncer_card_border_found_long);
        scanState = State.FOUND;
    }

    /**
     * Return the view to its initial state, where no card has been detected.
     */
    private void setStateNotFound() {
        if (scanState == State.NOT_FOUND) return;
        ViewExtensionsKt.startAnimation(viewFinderBorder, R.drawable.bouncer_card_border_not_found);
        ViewExtensionsKt.hide(cardPanTextView);
        ViewExtensionsKt.hide(cardNameTextView);
        scanState = State.NOT_FOUND;
    }

    /**
     * Flash the border around the card green to indicate that scanning was successful.
     */
    private void setStateCorrect() {
        if (scanState == State.CORRECT) return;
        ViewExtensionsKt.startAnimation(viewFinderBorder, R.drawable.bouncer_card_border_correct);
        scanState = State.CORRECT;
    }
}
