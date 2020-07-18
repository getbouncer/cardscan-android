package com.getbouncer.cardscan.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.FrameLayout
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getbouncer.cardscan.ui.analyzer.PaymentCardOcrState
import com.getbouncer.cardscan.ui.result.OcrResultAggregator
import com.getbouncer.cardscan.ui.result.PaymentCardOcrResult
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.SavedFrame
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.seconds
import com.getbouncer.scan.payment.card.formatPan
import com.getbouncer.scan.payment.card.getCardIssuer
import com.getbouncer.scan.payment.card.isPossiblyValidPan
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.common.calculateCardFinderCoordinatesFromObjectDetection
import com.getbouncer.scan.payment.ml.ssd.DetectionBox
import com.getbouncer.scan.ui.DebugDetectionBox
import com.getbouncer.scan.ui.ScanActivity
import com.getbouncer.scan.ui.util.fadeIn
import com.getbouncer.scan.ui.util.fadeOut
import com.getbouncer.scan.ui.util.getColorByRes
import com.getbouncer.scan.ui.util.setAnimated
import com.getbouncer.scan.ui.util.setVisible
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.cameraPreviewHolder
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.cardNameTextView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.cardPanTextView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.cardscanLogo
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.closeButtonView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.debugBitmapView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.debugOverlayView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.debugWindowView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.enterCardManuallyButtonView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.flashButtonView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.instructionsTextView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.securityTextView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.viewFinderBackground
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.viewFinderBorder
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.viewFinderWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private const val REQUEST_CODE = 21521 // "bou"

private val MINIMUM_RESOLUTION = Size(1280, 720) // minimum size of an object square

private enum class State(val value: Int) {
    NOT_FOUND(0),
    FOUND(1);
}

fun DetectionBox.forDebugPan() = DebugDetectionBox(rect, confidence, label.toString())
fun DetectionBox.forDebugObjDetect(cardFinder: Rect, previewImage: Size) = DebugDetectionBox(
    calculateCardFinderCoordinatesFromObjectDetection(rect, previewImage, cardFinder), confidence, label.toString()
)

interface CardScanActivityResultHandler {
    /**
     * A payment card was successfully scanned.
     */
    fun cardScanned(scanId: String?, scanResult: CardScanActivityResult)

    /**
     * The user requested to enter payment card details manually.
     */
    fun enterManually(scanId: String?)

    /**
     * The user canceled the scan.
     */
    fun userCanceled(scanId: String?)

    /**
     * The scan failed because of a camera error.
     */
    fun cameraError(scanId: String?)

    /**
     * The scan failed to analyze images from the camera.
     */
    fun analyzerFailure(scanId: String?)

    /**
     * The scan was canceled due to unknown reasons.
     */
    fun canceledUnknown(scanId: String?)
}

@Parcelize
data class CardScanActivityResult(
    val pan: String?,
    val expiryDay: String?,
    val expiryMonth: String?,
    val expiryYear: String?,
    val networkName: String?,
    val cvc: String?,
    val cardholderName: String?,
    val errorString: String?
) : Parcelable

class CardScanActivity :
    ScanActivity(),
    AggregateResultListener<SSDOcr.Input, PaymentCardOcrState, OcrResultAggregator.InterimResult, PaymentCardOcrResult>,
    AnalyzerLoopErrorListener {

    companion object {
        private const val PARAM_ENABLE_ENTER_MANUALLY = "enableEnterManually"
        private const val PARAM_DISPLAY_CARD_PAN = "displayCardPan"
        private const val PARAM_DISPLAY_CARD_SCAN_LOGO = "displayCardScanLogo"
        private const val PARAM_DISPLAY_CARDHOLDER_NAME = "displayCardholderName"
        private const val PARAM_ENABLE_EXPIRY_EXTRACTION = "enableExpiryExtraction"
        private const val PARAM_ENABLE_NAME_EXTRACTION = "enableNameExtraction"

        private const val CANCELED_REASON_ENTER_MANUALLY = 3

        private const val RESULT_SCANNED_CARD = "scannedCard"

        /**
         * Warm up the analyzers for card scanner. This method is optional, but will increase the
         * speed at which the scan occurs.
         *
         * @param context: A context to use for warming up the analyzers.
         */
        @JvmStatic
        fun warmUp(context: Context, apiKey: String, initializeNameAndExpiryExtraction: Boolean) {
            CardScanFlow.warmUp(context, apiKey, initializeNameAndExpiryExtraction)
        }

        /**
         * Start the card scanner activity.
         *
         * @param activity: The activity launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
         * @param enableNameExtraction: If true, attempt to extract the cardholder name.
         * @param displayCardPan: If true, display the card pan once the card has started to scan.
         * @param displayCardholderName: If true, display the name of the card owner if extracted.
         * @param displayCardScanLogo: If true, display the cardscan.io logo at the top of the
         *     screen.
         * @param enableDebug: If true, enable debug views in card scan.
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            activity: Activity,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
            enableExpiryExtraction: Boolean = false,
            enableNameExtraction: Boolean = false,
            displayCardPan: Boolean = true,
            displayCardholderName: Boolean = true,
            displayCardScanLogo: Boolean = true,
            enableDebug: Boolean = Config.isDebug
        ) {
            activity.startActivityForResult(
                buildIntent(
                    context = activity,
                    apiKey = apiKey,
                    enableEnterCardManually = enableEnterCardManually,
                    enableExpiryExtraction = enableExpiryExtraction,
                    enableNameExtraction = enableNameExtraction,
                    displayCardPan = displayCardPan,
                    displayCardholderName = displayCardholderName,
                    displayCardScanLogo = displayCardScanLogo,
                    enableDebug = enableDebug
                ),
                REQUEST_CODE
            )
        }

        /**
         * Start the card scanner activity.
         *
         * @param fragment: The fragment launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
         * @param enableNameExtraction: If true, attempt to extract the cardholder name.
         * @param displayCardPan: If true, display the card pan once the card has started to scan.
         * @param displayCardholderName: If true, display the name of the card owner if extracted.
         * @param displayCardScanLogo: If true, display the cardscan.io logo at the top of the
         *     screen.
         * @param enableDebug: If true, enable debug views in card scan.
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            fragment: Fragment,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
            enableExpiryExtraction: Boolean = false,
            enableNameExtraction: Boolean = false,
            displayCardPan: Boolean = false,
            displayCardholderName: Boolean = false,
            displayCardScanLogo: Boolean = true,
            enableDebug: Boolean = Config.isDebug
        ) {
            val context = fragment.context ?: return
            fragment.startActivityForResult(
                buildIntent(
                    context = context,
                    apiKey = apiKey,
                    enableEnterCardManually = enableEnterCardManually,
                    enableExpiryExtraction = enableExpiryExtraction,
                    enableNameExtraction = enableNameExtraction,
                    displayCardPan = displayCardPan,
                    displayCardholderName = displayCardholderName,
                    displayCardScanLogo = displayCardScanLogo,
                    enableDebug = enableDebug
                ),
                REQUEST_CODE
            )
        }

        /**
         * Build an intent that can be used to start the card scanner activity.
         *
         * @param context: The activity used to build the intent.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
         * @param enableNameExtraction: If true, attempt to extract the cardholder name.
         * @param displayCardPan: If true, display the card pan once the card has started to scan.
         * @param displayCardholderName: If true, display the name of the card owner if extracted.
         * @param displayCardScanLogo: If true, display the cardscan.io logo at the top of the
         *     screen.
         * @param enableDebug: If true, enable debug views in card scan.
         */
        @JvmStatic
        @JvmOverloads
        fun buildIntent(
            context: Context,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
            enableExpiryExtraction: Boolean = false,
            enableNameExtraction: Boolean = false,
            displayCardPan: Boolean = false,
            displayCardholderName: Boolean = false,
            displayCardScanLogo: Boolean = true,
            enableDebug: Boolean = Config.isDebug
        ): Intent {
            Config.apiKey = apiKey
            Config.isDebug = enableDebug

            return Intent(context, CardScanActivity::class.java)
                .putExtra(PARAM_DISPLAY_CARD_SCAN_LOGO, displayCardScanLogo)
                .putExtra(PARAM_ENABLE_ENTER_MANUALLY, enableEnterCardManually)
                .putExtra(PARAM_ENABLE_EXPIRY_EXTRACTION, enableExpiryExtraction)
                .putExtra(PARAM_ENABLE_NAME_EXTRACTION, enableNameExtraction)
                .putExtra(PARAM_DISPLAY_CARD_PAN, displayCardPan)
                .putExtra(PARAM_DISPLAY_CARDHOLDER_NAME, displayCardholderName)
        }

        @JvmStatic
        fun parseScanResult(
            resultCode: Int,
            data: Intent?,
            handler: CardScanActivityResultHandler
        ) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val scanResult: CardScanActivityResult? = data.getParcelableExtra(RESULT_SCANNED_CARD)
                if (scanResult != null) {
                    handler.cardScanned(data.scanId(), scanResult)
                } else {
                    handler.canceledUnknown(data.scanId())
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                when {
                    data.isUserCanceled() -> handler.userCanceled(data.scanId())
                    data.isCameraError() -> handler.cameraError(data.scanId())
                    data.isAnalyzerFailure() -> handler.analyzerFailure(data.scanId())
                    getCanceledReason(data) == CANCELED_REASON_ENTER_MANUALLY -> {
                        handler.enterManually(data.scanId())
                    }
                }
            }
        }

        /**
         * A helper method to determine if an activity result came from card scan.
         */
        @JvmStatic
        fun isScanResult(requestCode: Int) = REQUEST_CODE == requestCode
    }

    private val enableEnterCardManually: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_ENTER_MANUALLY, false)
    }

    private val displayCardPan: Boolean by lazy {
        intent.getBooleanExtra(PARAM_DISPLAY_CARD_PAN, false)
    }

    private val displayCardholderName: Boolean by lazy {
        intent.getBooleanExtra(PARAM_DISPLAY_CARDHOLDER_NAME, false)
    }

    private val displayCardScanLogo: Boolean by lazy {
        intent.getBooleanExtra(PARAM_DISPLAY_CARD_SCAN_LOGO, true)
    }

    private val enableNameExtraction: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_NAME_EXTRACTION, true)
    }

    private val enableExpiryExtraction: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_EXPIRY_EXTRACTION, true)
    }

    private var mainLoopIsProducingResults = AtomicBoolean(false)
    private val hasPreviousValidResult = AtomicBoolean(false)
    private var lastDebugFrameUpdate = Clock.markNow()

    private val cardScanFlow: CardScanFlow by lazy {
        CardScanFlow(enableNameExtraction, enableExpiryExtraction, this, this)
    }

    private val viewFinderRect by lazy {
        Rect(
            viewFinderWindow.left,
            viewFinderWindow.top,
            viewFinderWindow.right,
            viewFinderWindow.bottom
        )
    }

    override val minimumAnalysisResolution: Size = MINIMUM_RESOLUTION

    override val previewFrame: FrameLayout by lazy { cameraPreviewHolder }

    /**
     * During on create
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!CardScanFlow.attemptedNameAndExpiryInitialization && (enableExpiryExtraction || enableNameExtraction)) {
            Log.e(
                Config.logTag,
                "Attempting to run name and expiry without initializing text detector. " +
                    "Please invoke the warmup() function with initializeNameAndExpiryExtraction to true."
            )
            cardScanFlow.cancelFlow()
            showNameAndExpiryInitializationError()
        }

        if (enableEnterCardManually) {
            enterCardManuallyButtonView.visibility = View.VISIBLE
        }

        if (Config.isDebug) {
            debugWindowView.visibility = View.VISIBLE
        }

        closeButtonView.setOnClickListener { userCancelScan() }
        enterCardManuallyButtonView.setOnClickListener { enterCardManually() }
        flashButtonView.setOnClickListener { toggleFlashlight() }

        viewFinderWindow.setOnTouchListener { _, e ->
            setFocus(PointF(e.x, e.y))
            true
        }

        if (!displayCardScanLogo) {
            cardscanLogo.visibility = View.INVISIBLE
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cardScanFlow.cancelFlow()
    }

    override fun onFlashlightStateChanged(flashlightOn: Boolean) {
        updateIcons()
    }

    override fun onPause() {
        super.onPause()
        viewFinderBackground.clearOnDrawListener()
    }

    override fun onResume() {
        super.onResume()
        setStateNotFound()
        viewFinderBackground.setOnDrawListener { updateIcons() }
    }

    private fun updateIcons() {
        val luminance = viewFinderBackground.getBackgroundLuminance()
        if (luminance > 127) {
            setIconsLight()
        } else {
            setIconsDark()
        }
    }

    private fun setIconsDark() {
        if (isFlashlightOn) {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_on_dark)
        } else {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_off_dark)
        }
        instructionsTextView.setTextColor(ContextCompat.getColor(this, R.color.bouncerInstructionsColorDark))
        securityTextView.setTextColor(ContextCompat.getColor(this, R.color.bouncerSecurityColorDark))
        enterCardManuallyButtonView.setTextColor(ContextCompat.getColor(this, R.color.bouncerEnterCardManuallyColorDark))
        closeButtonView.setImageResource(R.drawable.bouncer_close_button_dark)
        cardscanLogo.setImageResource(R.drawable.bouncer_logo_dark_background)
    }

    private fun setIconsLight() {
        if (isFlashlightOn) {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_on_light)
        } else {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_off_light)
        }
        instructionsTextView.setTextColor(ContextCompat.getColor(this, R.color.bouncerInstructionsColorLight))
        securityTextView.setTextColor(ContextCompat.getColor(this, R.color.bouncerSecurityColorLight))
        enterCardManuallyButtonView.setTextColor(ContextCompat.getColor(this, R.color.bouncerEnterCardManuallyColorLight))
        closeButtonView.setImageResource(R.drawable.bouncer_close_button_light)
        cardscanLogo.setImageResource(R.drawable.bouncer_logo_light_background)
    }

    /**
     * Cancel scanning to enter a card manually
     */
    private fun enterCardManually() {
        runBlocking { scanStat.trackResult("enter_card_manually") }
        cancelScan(CANCELED_REASON_ENTER_MANUALLY)
    }

    /**
     * Card was successfully scanned, return an activity result.
     */
    private fun cardScanned(result: CardScanActivityResult) {
        runBlocking { scanStat.trackResult("card_scanned") }
        completeScan(Intent().putExtra(RESULT_SCANNED_CARD, result))
    }

    override fun onFlashSupported(supported: Boolean) {
        flashButtonView.setVisible(supported)
    }

    private var scanState = State.NOT_FOUND
    private fun setStateNotFound() {
        if (scanState != State.NOT_FOUND) {
            viewFinderBackground.setBackgroundColor(getColorByRes(R.color.bouncerNotFoundBackground))
            viewFinderWindow.setBackgroundResource(R.drawable.bouncer_card_background_not_found)
            setAnimated(viewFinderBorder, R.drawable.bouncer_card_border_not_found)
            cardPanTextView.setVisible(false)
            cardNameTextView.setVisible(false)
            instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
        }
        hasPreviousValidResult.set(false)
        scanState = State.NOT_FOUND
    }

    private fun setStateFoundShort() {
        setStateFound(R.drawable.bouncer_card_border_found)
    }

    private fun setStateFoundLong() {
        setStateFound(R.drawable.bouncer_card_border_found_long)
    }

    private fun setStateFound(@DrawableRes animation: Int) {
        if (scanState != State.FOUND) {
            viewFinderBackground.setBackgroundColor(getColorByRes(R.color.bouncerFoundBackground))
            viewFinderWindow.setBackgroundResource(R.drawable.bouncer_card_background_found)
            setAnimated(viewFinderBorder, animation)
            instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
        }
        scanState = State.FOUND
    }

    private fun showNameAndExpiryInitializationError() {
        AlertDialog.Builder(this)
            .setTitle(R.string.bouncer_name_and_expiry_initialization_error)
            .setMessage(R.string.bouncer_name_and_expiry_initialization_error_message)
            .setPositiveButton(R.string.bouncer_name_and_expiry_initialization_error_ok) { _, _ -> userCancelScan() }
            .setCancelable(false)
            .show()
    }

    override fun prepareCamera(onCameraReady: () -> Unit) {
        previewFrame.post {
            viewFinderBackground.setViewFinderRect(viewFinderRect)
            onCameraReady()
        }
    }

    /**
     * A final result was received from the aggregator. Set the result from this activity.
     */
    override suspend fun onResult(
        result: PaymentCardOcrResult,
        frames: Map<String, List<SavedFrame<SSDOcr.Input, PaymentCardOcrState, OcrResultAggregator.InterimResult>>>
    ) = launch(Dispatchers.Main) {
        /*
         * TODO: awushensky - I don't understand why, but withContext instead of launch suspends
         * indefinitely while using Camera1 APIs. My best guess is that camera1 is keeping the main
         * thread more tied up with preview than camera2 and cameraX do, and launch is allowing the
         * camera to close before suspending.
         */

        // Only show the expiry dates that are not expired
        val (expiryMonth, expiryYear) = if (result.expiry?.isValidExpiry() == true) {
            (result.expiry.month.toString() to result.expiry.year.toString())
        } else {
            (null to null)
        }

        cardScanned(
            CardScanActivityResult(
                pan = result.pan,
                networkName = getCardIssuer(result.pan).displayName,
                expiryDay = null,
                expiryMonth = expiryMonth,
                expiryYear = expiryYear,
                cvc = null,
                cardholderName = result.name,
                errorString = result.errorString
            )
        )
    }.let { Unit }

    private suspend fun showDebugFrame(
        frame: SSDOcr.Input,
        panBoxes: List<DetectionBox>?,
        objectBoxes: List<DetectionBox>?
    ) {
        if (Config.isDebug && lastDebugFrameUpdate.elapsedSince() > 1.seconds) {
            lastDebugFrameUpdate = Clock.markNow()
            val bitmap = withContext(Dispatchers.Default) { SSDOcr.cropImage(frame) }
            debugBitmapView.setImageBitmap(bitmap)
            if (panBoxes != null) {
                debugOverlayView.setBoxes(panBoxes.map { it.forDebugPan() })
            }
            if (objectBoxes != null) {
                debugOverlayView.setBoxes(objectBoxes.map { it.forDebugObjDetect(frame.cardFinder, frame.previewSize) })
            }

            Log.d(Config.logTag, "Delay between capture and result for this frame was ${frame.capturedAt.elapsedSince()}")
        }
    }

    /**
     * An interim result was received from the result aggregator.
     */
    override suspend fun onInterimResult(
        result: OcrResultAggregator.InterimResult,
        state: PaymentCardOcrState,
        frame: SSDOcr.Input
    ) = launch(Dispatchers.Main) {
        if (!mainLoopIsProducingResults.getAndSet(true)) {
            scanStat.trackResult("first_image_processed")
        }

        val hasPreviousValidResult = hasPreviousValidResult.getAndSet(result.hasValidPan)
        val isFirstValidResult = result.hasValidPan && !hasPreviousValidResult

        val pan = result.mostLikelyPan

        if (isFirstValidResult) {
            scanStat.trackResult("ocr_pan_observed")
            fadeOut(enterCardManuallyButtonView)
        }

        // if we're using debug, always show the latest number and name from the analyzer
        if (Config.isDebug) {
            if (displayCardPan) {
                cardPanTextView.text = formatPan(result.analyzerResult.pan ?: "")
                fadeIn(cardPanTextView, Duration.ZERO)
            }

            if (displayCardholderName) {
                cardNameTextView.text = result.analyzerResult.name ?: ""
                fadeIn(cardNameTextView, Duration.ZERO)
            }
        } else {
            if (displayCardPan && result.hasValidPan && !pan.isNullOrEmpty()) {
                cardPanTextView.text = formatPan(pan)
                fadeIn(cardPanTextView)
            }

            if (displayCardholderName && result.mostLikelyName != null) {
                cardNameTextView.text = result.mostLikelyName
                fadeIn(cardNameTextView)
            }
        }

        if (isPossiblyValidPan(pan)) {
            if ((enableNameExtraction || enableExpiryExtraction) && result.analyzerResult.isNameAndExpiryExtractionAvailable) {
                setStateFoundLong()
            } else {
                setStateFoundShort()
            }
        }

        showDebugFrame(frame, result.analyzerResult.panDetectionBoxes, result.analyzerResult.objDetectionBoxes)
    }.let { Unit }

    override suspend fun onReset() = launch(Dispatchers.Main) { setStateNotFound() }.let { Unit }

    override fun onAnalyzerFailure(t: Throwable): Boolean {
        analyzerFailureCancelScan(t)
        return true
    }

    override fun onResultFailure(t: Throwable): Boolean {
        analyzerFailureCancelScan(t)
        return true
    }

    override fun getLayoutRes(): Int = R.layout.bouncer_activity_card_scan

    /**
     * Once the camera stream is available, start processing images.
     */
    override fun onCameraStreamAvailable(cameraStream: Flow<Bitmap>) {
        cardScanFlow.startFlow(
            context = this,
            imageStream = cameraStream,
            previewSize = Size(previewFrame.width, previewFrame.height),
            viewFinder = viewFinderRect,
            lifecycleOwner = this,
            coroutineScope = this
        )
    }

    override fun onInvalidApiKey() {
        cardScanFlow.cancelFlow()
    }
}
