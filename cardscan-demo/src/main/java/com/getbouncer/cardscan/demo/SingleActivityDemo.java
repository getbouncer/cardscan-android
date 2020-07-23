package com.getbouncer.cardscan.demo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.Size;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.getbouncer.cardscan.ui.CardScanFlow;
import com.getbouncer.cardscan.ui.result.MainLoopAggregator;
import com.getbouncer.scan.camera.CameraErrorListener;
import com.getbouncer.scan.camera.camera2.Camera2Adapter;
import com.getbouncer.scan.framework.AggregateResultListener;
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener;
import com.getbouncer.scan.framework.Config;
import com.getbouncer.scan.framework.interop.BlockingAggregateResultListener;
import com.getbouncer.scan.framework.time.Clock;
import com.getbouncer.scan.framework.time.ClockMark;
import com.getbouncer.scan.payment.card.PanFormatterKt;
import com.getbouncer.scan.payment.card.PaymentCardUtils;
import com.getbouncer.scan.payment.ml.ExpiryDetect;
import com.getbouncer.scan.payment.ml.SSDOcr;
import com.getbouncer.scan.ui.ViewFinderBackground;
import com.getbouncer.scan.ui.util.ViewExtensionsKt;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;

import kotlin.Unit;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

public class SingleActivityDemo extends AppCompatActivity implements CameraErrorListener,
        AnalyzerLoopErrorListener, CoroutineScope {

    private enum State {
        NOT_FOUND,
        FOUND
    }

    private static final int PERMISSION_REQUEST_CODE = 1200;
    private static final Size MINIMUM_RESOLUTION = new Size(1280, 720);

    private static final int CANCELED_REASON_USER = -1;
    private static final int CANCELED_REASON_CAMERA_ERROR = -2;
    private static final int CANCELED_REASON_ANALYZER_FAILURE = -3;

    private Button scanCardButton;
    private View scanView;

    private TextureView cameraPreview;

    private Rect viewFinderRect;
    private FrameLayout viewFinderWindow;
    private ViewFinderBackground viewFinderBackground;
    private ImageView viewFinderBorder;

    private ImageView flashButtonView;
    private ImageView cardScanLogoView;
    private ImageView closeButtonView;

    private TextView instructionsTextView;
    private TextView securityTextView;
    private TextView enterCardManuallyButtonView;

    private TextView cardPanTextView;
    private TextView cardNameTextView;

    private ImageView debugBitmapView;

    private Camera2Adapter cameraAdapter;

    private CardScanFlow cardScanFlow;

    private final AtomicBoolean hasPreviousValidResult = new AtomicBoolean(false);
    private ClockMark lastDebugFrameUpdate = Clock.INSTANCE.markNow();
    private State scanState = State.NOT_FOUND;

    private boolean isScanning = false;

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
            isScanning = true;

            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) !=
                    PackageManager.PERMISSION_GRANTED) {
                requestCameraPermission();
            } else {
                prepareCamera();
            }
        });

        cameraPreview = findViewById(R.id.cameraPreviewHolder);
        viewFinderWindow = findViewById(R.id.viewFinderWindow);
        viewFinderBackground = findViewById(R.id.viewFinderBackground);
        viewFinderBorder = findViewById(R.id.viewFinderBorder);

        flashButtonView = findViewById(R.id.flashButtonView);
        cardScanLogoView = findViewById(R.id.cardscanLogo);
        closeButtonView = findViewById(R.id.closeButtonView);

        instructionsTextView = findViewById(R.id.instructionsTextView);
        securityTextView = findViewById(R.id.securityTextView);
        enterCardManuallyButtonView = findViewById(R.id.enterCardManuallyButtonView);

        cardPanTextView = findViewById(R.id.cardPanTextView);
        cardNameTextView = findViewById(R.id.cardNameTextView);

        debugBitmapView = findViewById(R.id.debugBitmapView);

        closeButtonView.setOnClickListener(v -> userCancelScan());
        flashButtonView.setOnClickListener(v -> toggleFlashlight());

        viewFinderWindow.setOnTouchListener((v, event) -> {
            setFocus(new PointF(event.getX(), event.getY()));
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
        viewFinderBackground.clearOnDrawListener();
        if (isScanning && cameraAdapter != null) {
            cameraAdapter.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        setStateNotFound();
        viewFinderBackground.setOnDrawListener(() -> {
            updateIcons(false);
            return Unit.INSTANCE;
        });
        if (isScanning) {
            prepareCamera();
        }
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
        @NonNull String[] permissions,
        @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                prepareCamera();
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
                (DialogInterface.OnClickListener) (dialog, which) -> prepareCamera()
            )
            .show();
    }

    private void prepareCamera() {
        cameraPreview.post(() -> {
            viewFinderRect = new Rect(
                viewFinderWindow.getLeft(),
                viewFinderWindow.getTop(),
                viewFinderWindow.getRight(),
                viewFinderWindow.getBottom()
            );
            viewFinderBackground.setViewFinderRect(viewFinderRect);

            cameraAdapter = new Camera2Adapter(this, cameraPreview, MINIMUM_RESOLUTION, this);
            cameraAdapter.bindToLifecycle(this);
            cameraAdapter.withFlashSupport(supported -> {
                setFlashlightState(cameraAdapter.isTorchOn());
                onFlashSupported(supported);
                return Unit.INSTANCE;
            });

            cardScanFlow = new CardScanFlow(true, true, aggregateResultListener, this);
            cardScanFlow.startFlow(
                this,
                cameraAdapter.getImageStream(),
                new Size(cameraPreview.getWidth(), cameraPreview.getHeight()),
                viewFinderRect,
                this,
                this
            );
        });
    }

    private void setFocus(PointF point) {
        cameraAdapter.setFocus(point);
    }

    /**
     * Turn the flashlight on or off.
     */
    private void setFlashlightState(boolean on) {
        if (cameraAdapter != null) {
            cameraAdapter.setTorchState(on);
            onFlashlightStateChanged(cameraAdapter.isTorchOn());
        }
    }

    private void onFlashlightStateChanged(boolean flashlightOn) {
        updateIcons(flashlightOn);
    }

    private void onFlashSupported(boolean supported) {
        flashButtonView.setVisibility(supported ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * Turn the flashlight on or off.
     */
    private void toggleFlashlight() {
        setFlashlightState(!cameraAdapter.isTorchOn());
    }

    private void updateIcons(boolean isFlashlightOn) {
        final int luminance = viewFinderBackground.getBackgroundLuminance();
        if (luminance > 127) {
            setIconsLight(isFlashlightOn);
        } else {
            setIconsDark(isFlashlightOn);
        }
    }

    private void setIconsDark(boolean isFlashlightOn) {
        if (isFlashlightOn) {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_on_dark);
        } else {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_off_dark);
        }
        instructionsTextView.setTextColor(
            ContextCompat.getColor(this, R.color.bouncerInstructionsColorDark)
        );
        securityTextView.setTextColor(
            ContextCompat.getColor(this, R.color.bouncerSecurityColorDark)
        );
        enterCardManuallyButtonView.setTextColor(
            ContextCompat.getColor(this, R.color.bouncerEnterCardManuallyColorDark)
        );
        closeButtonView.setImageResource(R.drawable.bouncer_close_button_dark);
        cardScanLogoView.setImageResource(R.drawable.bouncer_logo_dark_background);
    }

    private void setIconsLight(boolean isFlashlightOn) {
        if (isFlashlightOn) {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_on_light);
        } else {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_off_light);
        }
        instructionsTextView.setTextColor(
            ContextCompat.getColor(this, R.color.bouncerInstructionsColorLight)
        );
        securityTextView.setTextColor(
            ContextCompat.getColor(this, R.color.bouncerSecurityColorLight)
        );
        enterCardManuallyButtonView.setTextColor(
            ContextCompat.getColor(this, R.color.bouncerEnterCardManuallyColorLight)
        );
        closeButtonView.setImageResource(R.drawable.bouncer_close_button_light);
        cardScanLogoView.setImageResource(R.drawable.bouncer_logo_light_background);
    }

    /**
     * Cancel scanning due to analyzer failure
     */
    private void analyzerFailureCancelScan(@Nullable final Throwable cause) {
        Log.e(Config.getLogTag(), "Canceling scan due to analyzer error", cause);
        cancelScan(CANCELED_REASON_ANALYZER_FAILURE);
    }

    /**
     * Cancel scanning due to a camera error.
     */
    private void cameraErrorCancelScan(@Nullable final Throwable cause) {
        Log.e(Config.getLogTag(), "Canceling scan due to camera error", cause);
        cancelScan(CANCELED_REASON_CAMERA_ERROR);
    }

    /**
     * The scan has been cancelled by the user.
     */
    private void userCancelScan() {
        cancelScan(CANCELED_REASON_USER);
    }

    /**
     * Cancel a scan
     */
    private void cancelScan(int reasonCode) {
        new AlertDialog.Builder(this)
            .setMessage(String.format(Locale.getDefault(), "Scan Canceled: %d", reasonCode))
            .show();
        closeScanner();
    }

    /**
     * Complete a scan
     */
    private void completeScan(
        @Nullable Integer expiryMonth,
        @Nullable Integer expiryYear,
        @Nullable String cardNumber,
        @Nullable String issuer,
        @Nullable String name,
        @Nullable String error
    ) {
        new AlertDialog.Builder(this)
            .setMessage(String.format(
                Locale.getDefault(),
                "%s\n%s\n%d/%d\n%s\n%s",
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
        setFlashlightState(false);
        scanCardButton.setVisibility(View.VISIBLE);
        scanView.setVisibility(View.GONE);
        setStateNotFound();
        isScanning = false;
        if (cardScanFlow != null) {
            cardScanFlow.cancelFlow();
        }
        if (cameraAdapter != null) {
            cameraAdapter.onPause();
        }
    }

    @Override
    public void onCameraOpenError(@Nullable Throwable cause) {
        showCameraError(R.string.bouncer_error_camera_open, cause);
    }

    @Override
    public void onCameraAccessError(@Nullable Throwable cause) {
        showCameraError(R.string.bouncer_error_camera_access, cause);
    }

    @Override
    public void onCameraUnsupportedError(@Nullable Throwable cause) {
        showCameraError(R.string.bouncer_error_camera_unsupported, cause);
    }

    private void showCameraError(@StringRes int message, @Nullable Throwable cause) {
        new AlertDialog.Builder(this)
            .setTitle(R.string.bouncer_error_camera_title)
            .setMessage(message)
            .setPositiveButton(
                R.string.bouncer_error_camera_acknowledge_button,
                (dialog, which) -> cameraErrorCancelScan(cause)
            )
            .show();
    }

    private AggregateResultListener<
            MainLoopAggregator.InterimResult,
            MainLoopAggregator.FinalResult> aggregateResultListener =
            new BlockingAggregateResultListener<
                    MainLoopAggregator.InterimResult,
                    MainLoopAggregator.FinalResult>() {
        @Override
        public void onInterimResultBlocking(MainLoopAggregator.InterimResult interimResult) {
            boolean previousValidResult =
                    hasPreviousValidResult.getAndSet(interimResult.getHasValidPan());
            boolean isFirstValidResult = interimResult.getHasValidPan() && !previousValidResult;

            String pan = interimResult.getMostLikelyPan();

            new Handler(getMainLooper()).post(() -> {
                if (isFirstValidResult) {
                    enterCardManuallyButtonView.setVisibility(View.INVISIBLE);
                }

                if (interimResult.getHasValidPan() && pan != null && !pan.isEmpty()) {
                    cardPanTextView.setText(PanFormatterKt.formatPan(pan));
                    cardPanTextView.setVisibility(View.VISIBLE);
                }

                if (interimResult.getMostLikelyName() != null) {
                    cardNameTextView.setText(interimResult.getMostLikelyName());
                    cardNameTextView.setVisibility(View.VISIBLE);
                }

                if (PaymentCardUtils.isPossiblyValidPan(pan)) {
                    if (interimResult.getAnalyzerResult().isNameAndExpiryExtractionAvailable()) {
                        setStateFoundLong();
                    } else {
                        setStateFoundShort();
                    }
                }

                showDebugFrame(interimResult.getFrame());
            });
        }

        @Override
        public void onResultBlocking(MainLoopAggregator.FinalResult result) {
            // Only show the expiry dates that are not expired
            final ExpiryDetect.Expiry expiry = result.getExpiry();

            new Handler(getMainLooper()).post(() -> {
                if (expiry != null && expiry.isValidExpiry()) {
                    completeScan(
                        expiry.getMonth(),
                        expiry.getYear(),
                        result.getPan(),
                        PaymentCardUtils.getCardIssuer(result.getPan()).getDisplayName(),
                        result.getName(),
                        result.getErrorString()
                    );
                } else {
                    completeScan(
                        null,
                        null,
                        result.getPan(),
                        PaymentCardUtils.getCardIssuer(result.getPan()).getDisplayName(),
                        result.getName(),
                        result.getErrorString()
                    );
                }
            });
        }

        @Override
        public void onResetBlocking() {
            new Handler(getMainLooper()).post(() -> setStateNotFound());
        }
    };

    private void setStateFoundShort() {
        setStateFound(R.drawable.bouncer_card_border_found);
    }

    private void setStateFoundLong() {
        setStateFound(R.drawable.bouncer_card_border_found_long);
    }

    private void setStateFound(@DrawableRes int animation) {
        if (scanState != State.FOUND) {
            viewFinderBackground.setBackgroundColor(ViewExtensionsKt.getColorByRes(this,
                    R.color.bouncerFoundBackground));
            viewFinderWindow.setBackgroundResource(R.drawable.bouncer_card_background_found);
            ViewExtensionsKt.setAnimated(this, viewFinderBorder, animation);
            instructionsTextView.setText(R.string.bouncer_card_scan_instructions);
        }
        scanState = State.FOUND;
    }

    private void setStateNotFound() {
        if (scanState != State.NOT_FOUND) {
            viewFinderBackground.setBackgroundColor(ViewExtensionsKt.getColorByRes(this,
                    R.color.bouncerNotFoundBackground));
            viewFinderWindow.setBackgroundResource(R.drawable.bouncer_card_background_not_found);
            ViewExtensionsKt.setAnimated(this, viewFinderBorder,
                    R.drawable.bouncer_card_border_not_found);
            cardPanTextView.setVisibility(View.INVISIBLE);
            cardNameTextView.setVisibility(View.INVISIBLE);
            instructionsTextView.setText(R.string.bouncer_card_scan_instructions);
        }
        hasPreviousValidResult.set(false);
        scanState = State.NOT_FOUND;
    }

    private void showDebugFrame(final SSDOcr.Input frame) {
        if (Config.isDebug() && lastDebugFrameUpdate.elapsedSince().getInSeconds() > 1) {
            lastDebugFrameUpdate = Clock.INSTANCE.markNow();
            Bitmap bitmap = SSDOcr.Companion.cropImage(frame);
            debugBitmapView.setImageBitmap(bitmap);

            Log.d(
                Config.getLogTag(),
                String.format(
                    "Delay between capture and result for this frame was %s",
                    frame.getCapturedAt().elapsedSince()
                )
            );
        }
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

    @NotNull
    @Override
    public CoroutineContext getCoroutineContext() {
        return Dispatchers.getDefault();
    }
}
