package com.getbouncer.cardscan

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.PointF
import android.graphics.Rect
import android.os.Bundle
import android.util.Size
import android.view.TextureView
import android.view.View
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getbouncer.cardscan.base.AggregateResultListener
import com.getbouncer.cardscan.base.AnalyzerPool
import com.getbouncer.cardscan.base.Config
import com.getbouncer.cardscan.base.ProcessBoundAnalyzerLoop
import com.getbouncer.cardscan.base.ResultAggregator
import com.getbouncer.cardscan.base.ResultAggregatorConfig
import com.getbouncer.cardscan.base.SavedFrame
import com.getbouncer.cardscan.base.card.formatPan
import com.getbouncer.cardscan.base.card.getCardIssuer
import com.getbouncer.cardscan.base.card.isValidPan
import com.getbouncer.cardscan.base.image.PreviewImage
import com.getbouncer.cardscan.base.image.crop
import com.getbouncer.cardscan.base.image.scale
import com.getbouncer.cardscan.base.image.size
import com.getbouncer.cardscan.base.ml.SSDOcr
import com.getbouncer.cardscan.base.ml.card.OcrCardPan
import com.getbouncer.cardscan.base.ml.result.PaymentCardImageResultAggregator
import com.getbouncer.cardscan.base.time.Clock
import com.getbouncer.cardscan.base.time.ClockMark
import com.getbouncer.cardscan.base.time.seconds
import com.getbouncer.cardscan.ui.ScanActivity
import com.getbouncer.cardscan.ui.card.ScanResult
import com.getbouncer.cardscan.ui.util.fadeIn
import com.getbouncer.cardscan.ui.util.fadeOut
import com.getbouncer.cardscan.ui.util.getColorByRes
import com.getbouncer.cardscan.ui.util.setAnimated
import com.getbouncer.cardscan.ui.util.setVisible
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.cameraTexture
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.cardPanTextView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.cardscanLogo
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.closeButtonView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.debugBitmapView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.debugOverlayView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.debugWindowView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.enterCardManuallyButtonView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.flashButtonView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.instructionsTextView
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.viewFinderBackground
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.viewFinderBorder
import kotlinx.android.synthetic.main.bouncer_activity_card_scan.viewFinderWindow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val REQUEST_CODE = 21521 // "bou"

private val MINIMUM_RESOLUTION = Size(750, 750) // minimum size of an object square

enum class State(val value: Int) {
    NOT_FOUND(0),
    FOUND(1),
    WRONG(2);
}

interface CardScanActivityResultHandler {
    /**
     * A payment card was successfully scanned.
     */
    fun cardScanned(scanId: String?, scanResult: ScanResult)

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

class CardScanActivity : ScanActivity<Unit, OcrCardPan, String>(),
    AggregateResultListener<PreviewImage, Unit, OcrCardPan, String> {

    companion object {
        private const val PARAM_REQUIRED_CARD_NUMBER = "requiredCardNumber"
        private const val PARAM_ENABLE_ENTER_MANUALLY = "enableEnterManually"
        private const val PARAM_DISPLAY_CARD_PAN = "displayCardPan"
        private const val PARAM_DISPLAY_CARD_SCAN_LOGO = "displayCardScanLogo"

        private const val CANCELED_REASON_ENTER_MANUALLY = 3

        private const val RESULT_SCANNED_CARD = "scannedCard"

        /**
         * Warm up the analyzers for card scanner. This method is optional, but will increase the
         * speed at which the scan occurs.
         *
         * @param context: A context to use for warming up the analyzers.
         */
        @JvmStatic
        fun warmUp(context: Context) {
            GlobalScope.launch(Dispatchers.IO) { supervisorScope {
                analyzerPool = getAnalyzerPool(context)
            } }
        }

        /**
         * Start the card scanner activity.
         *
         * @param activity: The activity launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param displayCardPan: If true, display the card pan once the card has started to scan.
         * @param requiredCardNumber: If not null, card scan will display an error when scanning a
         *     card that does not match.
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
            displayCardPan: Boolean = false,
            requiredCardNumber: String? = null,
            displayCardScanLogo: Boolean = true,
            enableDebug: Boolean = Config.isDebug
        ) {
            activity.startActivityForResult(
                buildIntent(
                    context = activity,
                    apiKey = apiKey,
                    enableEnterCardManually = enableEnterCardManually,
                    displayCardPan = displayCardPan,
                    requiredCardNumber = requiredCardNumber,
                    displayCardScanLogo = displayCardScanLogo,
                    enableDebug = enableDebug
                ), REQUEST_CODE
            )
        }

        /**
         * Start the card scanner activity.
         *
         * @param fragment: The fragment launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param displayCardPan: If true, display the card pan once the card has started to scan.
         * @param requiredCardNumber: If not null, card scan will display an error when scanning a
         *     card that does not match.
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
            displayCardPan: Boolean = false,
            requiredCardNumber: String? = null,
            displayCardScanLogo: Boolean = true,
            enableDebug: Boolean = Config.isDebug
        ) {
            val context = fragment.context ?: return
            fragment.startActivityForResult(
                buildIntent(
                    context = context,
                    apiKey = apiKey,
                    enableEnterCardManually = enableEnterCardManually,
                    displayCardPan = displayCardPan,
                    requiredCardNumber = requiredCardNumber,
                    displayCardScanLogo = displayCardScanLogo,
                    enableDebug = enableDebug
                ), REQUEST_CODE
            )
        }

        /**
         * Build an intent that can be used to start the card scanner activity.
         *
         * @param context: The activity used to build the intent.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param displayCardPan: If true, display the card pan once the card has started to scan.
         * @param requiredCardNumber: If not null, card scan will display an error when scanning a
         *     card that does not match.
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
            displayCardPan: Boolean = false,
            requiredCardNumber: String? = null,
            displayCardScanLogo: Boolean = true,
            enableDebug: Boolean = Config.isDebug
        ): Intent {
            Config.apiKey = apiKey
            Config.isDebug = enableDebug

            val intent = Intent(context, CardScanActivity::class.java)
                .putExtra(PARAM_DISPLAY_CARD_SCAN_LOGO, displayCardScanLogo)
                .putExtra(PARAM_ENABLE_ENTER_MANUALLY, enableEnterCardManually)
                .putExtra(PARAM_DISPLAY_CARD_PAN, displayCardPan)

            if (requiredCardNumber != null) {
                intent.putExtra(PARAM_REQUIRED_CARD_NUMBER, requiredCardNumber)
            }

            return intent
        }

        @JvmStatic
        fun parseScanResult(resultCode: Int, data: Intent?, handler: CardScanActivityResultHandler) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val scanResult: ScanResult? = data.getParcelableExtra(RESULT_SCANNED_CARD)
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

        private val analyzerPoolMutex = Mutex()
        private var analyzerPool: AnalyzerPool<PreviewImage, Unit, OcrCardPan>? = null
        private suspend fun getAnalyzerPool(context: Context):
                AnalyzerPool<PreviewImage, Unit, OcrCardPan> = analyzerPoolMutex.withLock {
            var analyzerPool = analyzerPool
            if (analyzerPool == null) {
                analyzerPool = AnalyzerPool.Factory(SSDOcr.Factory(context, SSDOcr.ModelLoader(context))).buildAnalyzerPool()
                Companion.analyzerPool = analyzerPool
            }

            return analyzerPool
        }
    }

    private var lastWrongCard: ClockMark? = null
    private val showWrongDuration = 1.seconds

    private val enableEnterCardManually: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_ENTER_MANUALLY, false)
    }
    private val displayCardPan: Boolean by lazy {
        intent.getBooleanExtra(PARAM_DISPLAY_CARD_PAN, false)
    }
    private val requiredCardNumber: String? by lazy {
        intent.getStringExtra(PARAM_REQUIRED_CARD_NUMBER)
    }
    private val displayCardScanLogo: Boolean by lazy {
        intent.getBooleanExtra(PARAM_DISPLAY_CARD_SCAN_LOGO, true)
    }

    private val mainLoopIsProducingResultsMutex = Mutex()
    private var mainLoopIsProducingResults: Boolean = false

    override val viewFinderRect by lazy {
        Rect(
            viewFinderWindow.left,
            viewFinderWindow.top,
            viewFinderWindow.right,
            viewFinderWindow.bottom
        )
    }

    override val minimumAnalysisResolution: Size = MINIMUM_RESOLUTION

    override val previewTextureView: TextureView? by lazy { cameraTexture }

    /**
     * During on create
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

    override fun onFlashlightStateChanged(flashlightOn: Boolean) {
        updateIcons()
    }

    override fun onResume() {
        super.onResume()
        viewFinderBackground.setOnDrawListener { updateIcons() }
    }

    override fun onPause() {
        super.onPause()
        setStateNotFound()
        viewFinderBackground.clearOnDrawListener()
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
        instructionsTextView.setTextColor(ContextCompat.getColor(this,
            R.color.bouncerInstructionsColorDark
        ))
        enterCardManuallyButtonView.setTextColor(ContextCompat.getColor(this,
            R.color.bouncerEnterCardManuallyColorDark
        ))
        closeButtonView.setImageResource(R.drawable.bouncer_close_button_dark)
        cardscanLogo.setImageResource(R.drawable.bouncer_logo_dark_background)
    }

    private fun setIconsLight() {
        if (isFlashlightOn) {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_on_light)
        } else {
            flashButtonView.setImageResource(R.drawable.bouncer_flash_off_light)
        }
        instructionsTextView.setTextColor(ContextCompat.getColor(this,
            R.color.bouncerInstructionsColorLight
        ))
        enterCardManuallyButtonView.setTextColor(ContextCompat.getColor(this,
            R.color.bouncerEnterCardManuallyColorLight
        ))
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
    private fun cardScanned(result: ScanResult) {
        runBlocking { scanStat.trackResult("card_scanned") }
        completeScan(Intent().putExtra(RESULT_SCANNED_CARD, result))
    }

    override fun onFlashSupported(supported: Boolean) {
        flashButtonView.setVisible(supported)
    }

    override fun buildResultAggregator() = PaymentCardImageResultAggregator(
        config = ResultAggregatorConfig.Builder()
            .withMaxTotalAggregationTime(2.seconds)
            .withDefaultMaxSavedFrames(0)
            .build(),
        listener = this,
        name = "main_loop",
        requiredCardNumber = requiredCardNumber,
        requiredAgreementCount = 5
    )

    override fun buildMainLoop(
        resultAggregator: ResultAggregator<PreviewImage, Unit, OcrCardPan, String>
    ): ProcessBoundAnalyzerLoop<PreviewImage, Unit, OcrCardPan> =
        ProcessBoundAnalyzerLoop(
            analyzerPool = runBlocking { getAnalyzerPool(this@CardScanActivity) },
            resultHandler = resultAggregator,
            initialState = Unit,
            name = "main_loop",
            onAnalyzerFailure = {
                analyzerFailureCancelScan(it)
                true // terminate the loop on any analyzer failures
            },
            onResultFailure = {
                analyzerFailureCancelScan(it)
                true // terminate the loop on any result failures
            }
        )

    private var scanState = State.NOT_FOUND
    private fun setStateNotFound() {
        if (scanState != State.NOT_FOUND) {
            viewFinderBackground.setBackgroundColor(getColorByRes(R.color.bouncerNotFoundBackground))
            viewFinderWindow.setBackgroundResource(R.drawable.bouncer_card_background_not_found)
            setAnimated(viewFinderBorder, R.drawable.bouncer_card_border_not_found)
            cardPanTextView.setVisible(false)
        }
        scanState = State.NOT_FOUND
    }

    private fun setStateFound() {
        if (scanState != State.FOUND) {
            viewFinderBackground.setBackgroundColor(getColorByRes(R.color.bouncerFoundBackground))
            viewFinderWindow.setBackgroundResource(R.drawable.bouncer_card_background_found)
            setAnimated(viewFinderBorder, R.drawable.bouncer_card_border_found)
        }
        scanState = State.FOUND
    }

    private fun setStateWrong() {
        if (scanState != State.WRONG) {
            viewFinderBackground.setBackgroundColor(getColorByRes(R.color.bouncerWrongBackground))
            viewFinderWindow.setBackgroundResource(R.drawable.bouncer_card_background_wrong)
            setAnimated(viewFinderBorder, R.drawable.bouncer_card_border_wrong)
        }
        scanState = State.WRONG
    }

    override fun prepareCamera(onCameraReady: () -> Unit) {
        cameraTexture.post {
            viewFinderBackground.setViewFinderRect(viewFinderRect)
            onCameraReady()
        }
    }

    /**
     * A final result was received from the aggregator. Set the result from this activity.
     */
    override suspend fun onResult(
        result: String,
        frames: Map<String, List<SavedFrame<PreviewImage, Unit, OcrCardPan>>>
    ) = cardScanned(ScanResult(
        pan = result,
        networkName = getCardIssuer(result).displayName,
        expiryDay = null,
        expiryMonth = null,
        expiryYear = null,
        cvc = null,
        legalName = null
    ))

    /**
     * An interim result was received from the result aggregator.
     */
    override suspend fun onInterimResult(
        result: OcrCardPan,
        state: Unit,
        frame: PreviewImage,
        isFirstValidResult: Boolean
    ) {
        if (Config.isDebug) {
            debugBitmapView.setImageBitmap(frame.fullImage.crop(SSDOcr.calculateCrop(
                frame.fullImage.size(),
                frame.previewSize,
                frame.cardFinder
            )).scale(SSDOcr.Factory.TRAINED_IMAGE_SIZE))
            debugOverlayView.setBoxes(result.detectedBoxes)
        }

        mainLoopIsProducingResultsMutex.withLock {
            if (!mainLoopIsProducingResults) {
                mainLoopIsProducingResults = true
                scanStat.trackResult("first_image_processed")
            }
        }

        val pan = result.pan

        if (isFirstValidResult) {
            scanStat.trackResult("ocr_pan_observed")
            fadeOut(enterCardManuallyButtonView)

            if (displayCardPan) {
                cardPanTextView.text = formatPan(pan)
                fadeIn(cardPanTextView)
            }
        }

        instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
        setStateFound()
    }

    override suspend fun onInvalidResult(
        result: OcrCardPan,
        state: Unit,
        frame: PreviewImage,
        hasPreviousValidResult: Boolean
    ) {
        if (Config.isDebug) {
            debugBitmapView.setImageBitmap(frame.fullImage.crop(SSDOcr.calculateCrop(
                frame.fullImage.size(),
                frame.previewSize,
                frame.cardFinder
            )))
            debugOverlayView.setBoxes(result.detectedBoxes)
        }

        mainLoopIsProducingResultsMutex.withLock {
            if (!mainLoopIsProducingResults) {
                mainLoopIsProducingResults = true
                scanStat.trackResult("first_image_processed")
            }
        }

        if (isValidPan(result.pan) && !hasPreviousValidResult) {
            lastWrongCard = Clock.markNow()
            if (requiredCardNumber != null) {
                instructionsTextView.text = getString(
                    R.string.bouncer_scanned_wrong_card,
                    requiredCardNumber?.takeLast(4) ?: ""
                )
            }
            setStateWrong()
        } else if (!isValidPan(result.pan)) {
            val lastWrongCard = this.lastWrongCard
            if (scanState == State.WRONG &&
                (lastWrongCard == null || lastWrongCard.elapsedSince() > showWrongDuration)) {
                instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
                setStateNotFound()
            }
        }
    }

    override fun getLayoutRes(): Int = R.layout.bouncer_activity_card_scan
}
