package com.getbouncer.cardscan.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import com.getbouncer.cardscan.ui.result.MainLoopAggregator
import com.getbouncer.cardscan.ui.result.MainLoopState
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.payment.card.formatPan
import com.getbouncer.scan.payment.cropCameraPreviewToSquare
import com.getbouncer.scan.payment.cropCameraPreviewToViewFinder
import com.getbouncer.scan.payment.ml.ssd.DetectionBox
import com.getbouncer.scan.ui.CancellationReason
import com.getbouncer.scan.ui.DebugDetectionBox
import com.getbouncer.scan.ui.ScanResultListener
import com.getbouncer.scan.ui.SimpleScanActivity
import com.getbouncer.scan.ui.util.getColorByRes
import com.getbouncer.scan.ui.util.setTextSizeByRes
import com.getbouncer.scan.ui.util.setVisible
import com.getbouncer.scan.ui.util.show
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Deprecated("Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
interface CardScanResultListener : ScanResultListener {

    /**
     * A payment card was successfully scanned.
     */
    fun cardScanned(
        pan: String?,
        frames: Collection<SavedFrame>,
        isFastDevice: Boolean,
    )
}

private val MINIMUM_RESOLUTION = Size(1067, 600) // minimum size of screen detect

private fun DetectionBox.forDebug() = DebugDetectionBox(rect, confidence, label.toString())

@Deprecated("Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
abstract class CardScanBaseActivity :
    SimpleScanActivity(),
    AggregateResultListener<MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult>,
    AnalyzerLoopErrorListener {

    /**
     * The text view that lets a user manually enter a card.
     */
    protected open val enterCardManuallyTextView: TextView by lazy { TextView(this) }

    protected abstract val enableEnterCardManually: Boolean
    protected abstract val enableNameExtraction: Boolean
    protected abstract val enableExpiryExtraction: Boolean

    /**
     * The listener which handles results from the scan.
     */
    abstract override val resultListener: CardScanResultListener

    private var mainLoopIsProducingResults = AtomicBoolean(false)
    private val hasPreviousValidResult = AtomicBoolean(false)

    abstract override val scanFlow: CardScanFlow

    override val minimumAnalysisResolution: Size = MINIMUM_RESOLUTION

    /**
     * During on create
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enterCardManuallyTextView.setOnClickListener { enterCardManually() }
    }

    override fun addUiComponents() {
        super.addUiComponents()
        appendUiComponents(enterCardManuallyTextView)
    }

    override fun setupUiComponents() {
        super.setupUiComponents()

        enterCardManuallyTextView.text = getString(R.string.bouncer_enter_card_manually)
        enterCardManuallyTextView.setTextSizeByRes(R.dimen.bouncerEnterCardManuallyTextSize)
        enterCardManuallyTextView.gravity = Gravity.CENTER

        enterCardManuallyTextView.setVisible(enableEnterCardManually)

        if (isBackgroundDark()) {
            enterCardManuallyTextView.setTextColor(getColorByRes(R.color.bouncerEnterCardManuallyColorDark))
        } else {
            enterCardManuallyTextView.setTextColor(getColorByRes(R.color.bouncerEnterCardManuallyColorLight))
        }
    }

    override fun setupUiConstraints() {
        super.setupUiConstraints()

        enterCardManuallyTextView.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT, // width
            ConstraintLayout.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerEnterCardManuallyMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerEnterCardManuallyMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerEnterCardManuallyMargin)
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerEnterCardManuallyMargin)
        }

        enterCardManuallyTextView.addConstraints {
            connect(it.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    /**
     * Cancel scanning to enter a card manually
     */
    protected open fun enterCardManually() {
        runBlocking { scanStat.trackResult("enter_card_manually") }
        resultListener.userCanceled(CancellationReason.UserCannotScan)
        closeScanner()
    }

    /**
     * A final result was received from the aggregator.
     */
    override suspend fun onResult(result: MainLoopAggregator.FinalResult) = launch(Dispatchers.Main) {
        changeScanState(ScanState.Correct)
        cameraAdapter.unbindFromLifecycle(this@CardScanBaseActivity)
        resultListener.cardScanned(
            pan = result.pan,
            frames = scanFlow.selectCompletionLoopFrames(result.averageFrameRate, result.savedFrames),
            isFastDevice = result.averageFrameRate > Config.slowDeviceFrameRate,
        )
    }.let { }

    /**
     * An interim result was received from the result aggregator.
     */
    override suspend fun onInterimResult(result: MainLoopAggregator.InterimResult) = launch(Dispatchers.Main) {
        if (!mainLoopIsProducingResults.getAndSet(true)) {
            scanStat.trackResult("first_image_processed")
        }
        if (result.state is MainLoopState.PanFound && !hasPreviousValidResult.getAndSet(true)) {
            scanStat.trackResult("ocr_pan_observed")
        }

        if (Config.displayScanResult) {
            if (Config.isDebug && result.analyzerResult.ocr?.pan?.isNotEmpty() == true) {
                cardNumberTextView.text = formatPan(result.analyzerResult.ocr.pan)
                cardNumberTextView.show()
            } else {
                val mostLikelyPan = when (val state = result.state) {
                    is MainLoopState.Initial -> null
                    is MainLoopState.PanFound -> state.getMostLikelyPan()
                    is MainLoopState.PanSatisfied -> state.pan
                    is MainLoopState.CardSatisfied -> state.getMostLikelyPan()
                    is MainLoopState.Finished -> state.pan
                }
                if (mostLikelyPan?.isNotEmpty() == true) {
                    cardNumberTextView.text = formatPan(mostLikelyPan)
                    cardNumberTextView.show()
                }
            }
        }

        when (result.state) {
            is MainLoopState.Initial -> if (scanState !is ScanState.FoundLong) changeScanState(ScanState.NotFound)
            is MainLoopState.PanFound -> changeScanState(ScanState.FoundLong)
            is MainLoopState.PanSatisfied -> changeScanState(ScanState.FoundLong)
            is MainLoopState.CardSatisfied -> changeScanState(ScanState.FoundLong)
            is MainLoopState.Finished -> changeScanState(ScanState.Correct)
        }

        if (Config.isDebug) {
            result.analyzerResult.ocr?.detectedBoxes?.let { detectionBoxes ->
                val bitmap = withContext(Dispatchers.Default) {
                    cropCameraPreviewToViewFinder(
                        result.frame.cameraPreviewImage.image.image,
                        result.frame.cameraPreviewImage.previewImageBounds,
                        result.frame.cardFinder
                    )
                }
                debugImageView.setImageBitmap(bitmap)
                debugOverlayView.setBoxes(detectionBoxes.map { it.forDebug() })
            } ?: run {
                val bitmap = withContext(Dispatchers.Default) {
                    cropCameraPreviewToSquare(
                        result.frame.cameraPreviewImage.image.image,
                        result.frame.cameraPreviewImage.previewImageBounds,
                        result.frame.cardFinder
                    )
                }
                debugImageView.setImageBitmap(bitmap)
                debugOverlayView.clearBoxes()
            }
        }
    }.let { }

    override suspend fun onReset() = launch(Dispatchers.Main) { changeScanState(ScanState.NotFound) }.let { }

    override fun onAnalyzerFailure(t: Throwable): Boolean {
        analyzerFailureCancelScan(t)
        return true
    }

    override fun onResultFailure(t: Throwable): Boolean {
        analyzerFailureCancelScan(t)
        return true
    }
}
