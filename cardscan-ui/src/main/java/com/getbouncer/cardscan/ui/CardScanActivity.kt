package com.getbouncer.cardscan.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.fragment.app.Fragment
import com.getbouncer.cardscan.ui.analyzer.CompletionLoopAnalyzer
import com.getbouncer.cardscan.ui.result.CompletionLoopListener
import com.getbouncer.cardscan.ui.result.CompletionLoopResult
import com.getbouncer.cardscan.ui.result.MainLoopAggregator
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.payment.card.getCardIssuer
import com.getbouncer.scan.payment.card.isValidExpiry
import com.getbouncer.scan.payment.cropCameraPreviewToSquare
import com.getbouncer.scan.ui.util.getColorByRes
import com.getbouncer.scan.ui.util.hide
import com.getbouncer.scan.ui.util.setTextSizeByRes
import com.getbouncer.scan.ui.util.setVisible
import com.getbouncer.scan.ui.util.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

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
    val errorString: String?,
) : Parcelable

interface CardProcessedResultListener : CardScanResultListener {

    /**
     * A payment card was successfully scanned.
     */
    fun cardProcessed(scanResult: CardScanActivityResult)
}

open class CardScanActivity :
    CardScanBaseActivity(),
    AggregateResultListener<MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult>,
    CompletionLoopListener,
    AnalyzerLoopErrorListener {

    companion object {
        const val REQUEST_CODE = 21521 // "bou"

        const val PARAM_ENABLE_ENTER_MANUALLY = "enableEnterManually"
        const val PARAM_ENABLE_EXPIRY_EXTRACTION = "enableExpiryExtraction"
        const val PARAM_ENABLE_NAME_EXTRACTION = "enableNameExtraction"

        const val RESULT_INSTANCE_ID = "instanceId"
        const val RESULT_SCAN_ID = "scanId"

        const val RESULT_SCANNED_CARD = "scannedCard"

        const val RESULT_CANCELED_REASON = "canceledReason"
        const val CANCELED_REASON_USER = -1
        const val CANCELED_REASON_CAMERA_ERROR = -2
        const val CANCELED_REASON_ANALYZER_FAILURE = -3
        const val CANCELED_REASON_ENTER_MANUALLY = -4

        private fun getCanceledReason(data: Intent?): Int =
            data?.getIntExtra(RESULT_CANCELED_REASON, Int.MIN_VALUE) ?: Int.MIN_VALUE

        private fun Intent?.isUserCanceled(): Boolean = getCanceledReason(this) == CANCELED_REASON_USER
        private fun Intent?.isCameraError(): Boolean = getCanceledReason(this) == CANCELED_REASON_CAMERA_ERROR
        private fun Intent?.isAnalyzerFailure(): Boolean = getCanceledReason(this) == CANCELED_REASON_ANALYZER_FAILURE
        private fun Intent?.isEnterCardManually(): Boolean = getCanceledReason(this) == CANCELED_REASON_ENTER_MANUALLY

        private fun Intent?.instanceId(): String? = this?.getStringExtra(RESULT_INSTANCE_ID)
        private fun Intent?.scanId(): String? = this?.getStringExtra(RESULT_SCAN_ID)

        /**
         * Warm up the analyzers for card scanner. This method is optional, but will increase the
         * speed at which the scan occurs.
         *
         * @param context: A context to use for warming up the analyzers.
         * @param apiKey: the API key used to warm up the ML models
         * @param initializeNameAndExpiryExtraction: if true, include name and expiry extraction
         * @param forImmediateUse: if true, attempt to use cached models instead of downloading by default
         */
        @JvmStatic
        fun warmUp(context: Context, apiKey: String, initializeNameAndExpiryExtraction: Boolean, forImmediateUse: Boolean = false) {
            prepareScan(context, apiKey, initializeNameAndExpiryExtraction, forImmediateUse) { /* do nothing on init */ }
        }

        /**
         * Warm up the analyzers and suspend the thread until it has completed.
         *
         * @param context: A context to use for warming up the analyzers.
         * @param apiKey: the API key used to warm up the ML models
         * @param initializeNameAndExpiryExtraction: if true, include name and expiry extraction
         * @param forImmediateUse: if true, attempt to use cached models instead of downloading by default
         */
        @JvmStatic
        fun prepareScan(context: Context, apiKey: String, initializeNameAndExpiryExtraction: Boolean, forImmediateUse: Boolean, onPrepared: () -> Unit) =
            GlobalScope.launch { CardScanFlow.prepareScan(context, apiKey, initializeNameAndExpiryExtraction, forImmediateUse) }.invokeOnCompletion { onPrepared() }

        /**
         * Start the card scanner activity.
         *
         * @param activity: The activity launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
         * @param enableNameExtraction: If true, attempt to extract the cardholder name.
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            activity: Activity,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
            enableExpiryExtraction: Boolean = false,
            enableNameExtraction: Boolean = false,
        ) {
            val intent = buildIntent(
                context = activity,
                apiKey = apiKey,
                enableEnterCardManually = enableEnterCardManually,
                enableExpiryExtraction = enableExpiryExtraction,
                enableNameExtraction = enableNameExtraction,
            )

            activity.startActivityForResult(intent, REQUEST_CODE)
        }

        /**
         * Start the card scanner activity.
         *
         * @param fragment: The fragment launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
         * @param enableNameExtraction: If true, attempt to extract the cardholder name.
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            fragment: Fragment,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
            enableExpiryExtraction: Boolean = false,
            enableNameExtraction: Boolean = false,
        ) {
            val context = fragment.context ?: return
            val intent = buildIntent(
                context = context,
                apiKey = apiKey,
                enableEnterCardManually = enableEnterCardManually,
                enableExpiryExtraction = enableExpiryExtraction,
                enableNameExtraction = enableNameExtraction,
            )

            fragment.startActivityForResult(intent, REQUEST_CODE)
        }

        /**
         * Build an intent that can be used to start the card scanner activity.
         *
         * @param context: The activity used to build the intent.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
         * @param enableNameExtraction: If true, attempt to extract the cardholder name.
         */
        @JvmStatic
        @JvmOverloads
        fun buildIntent(
            context: Context,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
            enableExpiryExtraction: Boolean = false,
            enableNameExtraction: Boolean = false,
        ): Intent {
            Config.apiKey = apiKey

            return Intent(context, CardScanActivity::class.java)
                .putExtra(PARAM_ENABLE_ENTER_MANUALLY, enableEnterCardManually)
                .putExtra(PARAM_ENABLE_EXPIRY_EXTRACTION, enableExpiryExtraction)
                .putExtra(PARAM_ENABLE_NAME_EXTRACTION, enableNameExtraction)
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
                    data.isEnterCardManually() -> handler.enterManually(data.scanId())
                }
            }
        }

        /**
         * A helper method to determine if an activity result came from card scan.
         */
        @JvmStatic
        fun isScanResult(requestCode: Int) = REQUEST_CODE == requestCode

        /**
         * Determine if the scan is supported
         */
        @JvmStatic
        fun isSupported(context: Context) = CardScanFlow.isSupported(context)

        /**
         * Determine if the scan models are available (have been warmed up)
         */
        @JvmStatic
        fun isScanReady() = CardScanFlow.isScanReady()
    }

    /**
     * And overlay to darken the screen during result processing.
     */
    protected open val processingOverlayView by lazy { View(this) }

    /**
     * The spinner indicating that results are processing.
     */
    protected open val processingSpinnerView by lazy { ProgressBar(this) }

    /**
     * The text indicating that results are processing
     */
    protected open val processingTextView by lazy { TextView(this) }

    /**
     * The image view for debugging the completion loop
     */
    protected open val debugCompletionImageView by lazy { ImageView(this) }

    override fun addUiComponents() {
        super.addUiComponents()
        appendUiComponents(processingOverlayView, processingSpinnerView, processingTextView, debugCompletionImageView)
    }

    override fun setupUiComponents() {
        super.setupUiComponents()

        setupProcessingOverlayViewUi()
        setupProcessingTextViewUi()
        setupDebugCompletionViewUi()
    }

    protected open fun setupProcessingOverlayViewUi() {
        processingOverlayView.setBackgroundColor(getColorByRes(R.color.bouncerProcessingBackground))
    }

    protected open fun setupProcessingTextViewUi() {
        processingTextView.text = getString(R.string.bouncer_processing_card)
        processingTextView.setTextSizeByRes(R.dimen.bouncerProcessingTextSize)
        processingTextView.setTextColor(getColorByRes(R.color.bouncerProcessingText))
        processingTextView.gravity = Gravity.CENTER
    }

    protected open fun setupDebugCompletionViewUi() {
        debugCompletionImageView.contentDescription = getString(R.string.bouncer_debug_description)
        debugCompletionImageView.setVisible(Config.isDebug)
    }

    override fun setupUiConstraints() {
        super.setupUiConstraints()

        setupProcessingOverlayViewConstraints()
        setupProcessingSpinnerViewConstraints()
        setupProcessingTextViewConstraints()
        setupDebugCompletionViewConstraints()
    }

    protected open fun setupProcessingOverlayViewConstraints() {
        processingOverlayView.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT, // width
            ConstraintLayout.LayoutParams.MATCH_PARENT, // height
        )

        processingOverlayView.constrainToParent()
    }

    protected open fun setupProcessingSpinnerViewConstraints() {
        processingSpinnerView.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT, // width
            ConstraintLayout.LayoutParams.WRAP_CONTENT, // height
        )

        processingSpinnerView.constrainToParent()
    }

    protected open fun setupProcessingTextViewConstraints() {
        processingTextView.layoutParams = ConstraintLayout.LayoutParams(
            0, // width
            ConstraintLayout.LayoutParams.WRAP_CONTENT, // height
        )

        processingTextView.addConstraints {
            connect(it.id, ConstraintSet.TOP, processingSpinnerView.id, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    protected open fun setupDebugCompletionViewConstraints() {
        debugCompletionImageView.layoutParams = ConstraintLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.bouncerDebugWindowWidth), // width,
            resources.getDimensionPixelSize(R.dimen.bouncerDebugWindowWidth), // height
        )

        debugCompletionImageView.addConstraints {
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
            connect(it.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
        }
    }

    override fun displayState(newState: ScanState, previousState: ScanState?) {
        super.displayState(newState, previousState)

        when (newState) {
            is ScanState.NotFound, ScanState.FoundShort, ScanState.FoundLong, ScanState.Wrong -> {
                processingOverlayView.hide()
                processingSpinnerView.hide()
                processingTextView.hide()
            }
            is ScanState.Correct -> {
                processingOverlayView.show()
                processingSpinnerView.show()
                processingTextView.show()
            }
        }
    }

    override val scanFlow: CardScanFlow by lazy {
        CardScanFlow(enableNameExtraction, enableExpiryExtraction, this, this)
    }

    override val enableEnterCardManually: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_ENTER_MANUALLY, false)
    }

    override val enableNameExtraction: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_NAME_EXTRACTION, false)
    }

    override val enableExpiryExtraction: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_EXPIRY_EXTRACTION, false)
    }

    private var pan: String? = null

    override val resultListener = object : CardProcessedResultListener {
        override fun cardProcessed(scanResult: CardScanActivityResult) {
            val intent = Intent()
                .putExtra(RESULT_SCANNED_CARD, scanResult)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_OK, intent)
        }

        override fun cardScanned(
            pan: String?,
            frames: Collection<SavedFrame>,
            isFastDevice: Boolean
        ) {
            this@CardScanActivity.pan = pan
            launch(Dispatchers.Default) {
                scanFlow.launchCompletionLoop(
                    context = this@CardScanActivity,
                    completionResultListener = this@CardScanActivity,
                    savedFrames = frames,
                    isFastDevice = isFastDevice,
                    coroutineScope = this@CardScanActivity,
                )
            }
        }

        override fun enterManually() {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_ENTER_MANUALLY)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }

        override fun userCanceled() {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_USER)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }

        override fun cameraError(cause: Throwable?) {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_CAMERA_ERROR)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }

        override fun analyzerFailure(cause: Throwable?) {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_ANALYZER_FAILURE)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }
    }

    override fun onCompletionLoopDone(result: CompletionLoopResult) = launch(Dispatchers.Main) {
        scanStat.trackResult("card_scanned")

        // Only show the expiry dates that are not expired
        val (expiryMonth, expiryYear) = if (isValidExpiry(null, result.expiryMonth ?: "", result.expiryYear ?: "")) {
            (result.expiryMonth to result.expiryYear)
        } else {
            (null to null)
        }

        resultListener.cardProcessed(
            scanResult = CardScanActivityResult(
                pan = pan,
                expiryDay = null,
                expiryMonth = expiryMonth,
                expiryYear = expiryYear,
                networkName = getCardIssuer(pan).displayName,
                cvc = null,
                cardholderName = result.name,
                errorString = result.errorString,
            )
        )

        closeScanner()
    }.let { }

    override fun onCompletionLoopFrameProcessed(
        result: CompletionLoopAnalyzer.Prediction,
        frame: SavedFrame,
    ) = launch(Dispatchers.Main) {
        if (Config.isDebug) {
            val bitmap = withContext(Dispatchers.Default) {
                cropCameraPreviewToSquare(frame.frame.cameraPreviewImage.image.image, frame.frame.cameraPreviewImage.previewImageBounds, frame.frame.cardFinder)
            }
            debugCompletionImageView.setImageBitmap(bitmap)
        }
    }.let { }
}
