package com.getbouncer.cardscan.ui.result

import com.getbouncer.cardscan.ui.SavedFrame
import com.getbouncer.cardscan.ui.analyzer.CompletionLoopAnalyzer
import com.getbouncer.scan.framework.TerminatingResultHandler
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.util.FrameRateTracker
import com.getbouncer.scan.framework.util.ItemCounter
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.ml.ExpiryDetect

private const val MINIMUM_NAME_AGREEMENT = 2
private const val MINIMUM_EXPIRY_AGREEMENT = 2

private const val INSUFFICIENT_PERMISSIONS_PREFIX = "Insufficient API key permissions - "

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
interface CompletionLoopListener {
    fun onCompletionLoopDone(result: CompletionLoopResult)

    fun onCompletionLoopFrameProcessed(
        result: CompletionLoopAnalyzer.Prediction,
        frame: SavedFrame,
    )
}

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
data class CompletionLoopResult(
    val name: String? = null,
    val expiryMonth: String? = null,
    val expiryYear: String? = null,
    val errorString: String? = null,
)

/**
 * Collect the results from executing the completion loop across multiple saved images. Send the
 * collected results to the [listener].
 */
@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
class CompletionLoopAggregator(
    private val listener: CompletionLoopListener,
) : TerminatingResultHandler<SavedFrame, Unit, CompletionLoopAnalyzer.Prediction>(Unit) {

    private val frameRateTracker by lazy {
        FrameRateTracker("cardscan_completion_loop_aggregator", notifyInterval = Duration.ZERO)
    }

    private val nameCounter: ItemCounter<String> = ItemTotalCounter()
    private val expiryCounter: ItemCounter<ExpiryDetect.Expiry> = ItemTotalCounter()
    private val errors = mutableSetOf<String>()

    override suspend fun onResult(
        result: CompletionLoopAnalyzer.Prediction,
        data: SavedFrame,
    ) {
        result.nameAndExpiryResult?.let { prediction ->
            prediction.name?.let { nameCounter.countItem(it) }
            prediction.expiry?.let { expiryCounter.countItem(it) }
        }

        if (!result.isNameExtractionAvailable && result.enableNameExtraction) {
            errors.add("name")
        }

        if (!result.isExpiryExtractionAvailable && result.enableExpiryExtraction) {
            errors.add("expiry")
        }

        frameRateTracker.trackFrameProcessed()

        listener.onCompletionLoopFrameProcessed(result, data)
    }

    override suspend fun onAllDataProcessed() {
        val expiry = expiryCounter.getHighestCountItem(MINIMUM_EXPIRY_AGREEMENT)?.second
        listener.onCompletionLoopDone(
            CompletionLoopResult(
                name = nameCounter.getHighestCountItem(MINIMUM_NAME_AGREEMENT)?.second,
                expiryMonth = expiry?.month,
                expiryYear = expiry?.year,
                errorString = if (errors.isNotEmpty()) {
                    INSUFFICIENT_PERMISSIONS_PREFIX + errors.joinToString(",", prefix = "[", postfix = "]")
                } else null,
            )
        )
    }

    override suspend fun onTerminatedEarly() {
        val expiry = expiryCounter.getHighestCountItem(MINIMUM_EXPIRY_AGREEMENT)?.second
        listener.onCompletionLoopDone(
            CompletionLoopResult(
                name = nameCounter.getHighestCountItem(MINIMUM_NAME_AGREEMENT)?.second,
                expiryMonth = expiry?.month,
                expiryYear = expiry?.year,
                errorString = if (errors.isNotEmpty()) {
                    INSUFFICIENT_PERMISSIONS_PREFIX + errors.joinToString(",", prefix = "[", postfix = "]")
                } else null,
            )
        )
    }
}
