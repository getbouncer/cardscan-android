package com.getbouncer.cardscan.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.getbouncer.cardscan.ui.analyzer.CompletionLoopAnalyzer
import com.getbouncer.cardscan.ui.exception.UnknownScanException
import com.getbouncer.cardscan.ui.result.CompletionLoopListener
import com.getbouncer.cardscan.ui.result.CompletionLoopResult
import com.getbouncer.cardscan.ui.result.MainLoopAggregator
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.payment.card.getCardIssuer
import com.getbouncer.scan.payment.card.isValidExpiry
import com.getbouncer.scan.payment.cropCameraPreviewToSquare
import com.getbouncer.scan.ui.CancellationReason
import com.getbouncer.scan.ui.util.getColorByRes
import com.getbouncer.scan.ui.util.hide
import com.getbouncer.scan.ui.util.setTextSizeByRes
import com.getbouncer.scan.ui.util.setVisible
import com.getbouncer.scan.ui.util.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize

@Parcelize
data class ScannedCard(
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
    fun cardProcessed(scannedCard: ScannedCard)
}

internal const val INTENT_PARAM_REQUEST = "request"
internal const val INTENT_PARAM_RESULT = "result"

open class CardScanActivity :
    CardScanBaseActivity(),
    AggregateResultListener<MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult>,
    CompletionLoopListener,
    AnalyzerLoopErrorListener {

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

    private val params: CardScanSheetParams by lazy {
        intent.getParcelableExtra(INTENT_PARAM_REQUEST)
            ?: CardScanSheetParams(
                apiKey = "",
                enableEnterManually = true,
                enableNameExtraction = false,
                enableExpiryExtraction = false,
            )
    }

    override val enableEnterCardManually: Boolean by lazy { params.enableEnterManually }

    override val enableNameExtraction: Boolean by lazy { params.enableNameExtraction }

    override val enableExpiryExtraction: Boolean by lazy { params.enableExpiryExtraction }

    private var pan: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Config.apiKey = params.apiKey
    }

    override val resultListener = object : CardProcessedResultListener {
        override fun cardProcessed(scannedCard: ScannedCard) {
            val intent = Intent()
                .putExtra(
                    INTENT_PARAM_RESULT,
                    CardScanSheetResult.Completed(scannedCard)
                )
            setResult(Activity.RESULT_OK, intent)
            closeScanner()
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

        override fun userCanceled(reason: CancellationReason) {
            val intent = Intent()
                .putExtra(
                    INTENT_PARAM_RESULT,
                    CardScanSheetResult.Canceled(reason),
                )
            setResult(Activity.RESULT_CANCELED, intent)
        }

        override fun failed(cause: Throwable?) {
            val intent = Intent()
                .putExtra(
                    INTENT_PARAM_RESULT,
                    CardScanSheetResult.Failed(cause ?: UnknownScanException()),
                )
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
            scannedCard = ScannedCard(
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
