package com.getbouncer.cardscan.ui.result

import com.getbouncer.cardscan.ui.analyzer.PaymentCardOcrAnalyzer
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr

private const val INSUFFICIENT_PERMISSIONS_PREFIX = "Insufficient API key permissions - "

/**
 * Aggregate results from the main loop. Each frame will trigger an [InterimResult] to the [listener]. Once the
 * [MainLoopState.Finished] state is reached, a [FinalResult] will be sent to the [listener].
 *
 * This aggregator is a state machine. The full list of possible states are subclasses of the [MainLoopState] class.
 * This was written referencing this article: https://thoughtbot.com/blog/finite-state-machines-android-kotlin-good-times
 */
class MainLoopAggregator(
    listener: AggregateResultListener<InterimResult, FinalResult>,
    private val enableNameExtraction: Boolean = false,
    private val enableExpiryExtraction: Boolean = false,
) : ResultAggregator<SSDOcr.Input, MainLoopState, PaymentCardOcrAnalyzer.Prediction, MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult>(
    listener = listener,
    initialState = MainLoopState.Initial(
        enableNameExtraction = enableNameExtraction,
        enableExpiryExtraction = enableExpiryExtraction,
    )
) {
    data class FinalResult(
        val pan: String?,
        val name: String?,
        val expiry: ExpiryDetect.Expiry?,
        val errorString: String?,
    )

    data class InterimResult(
        val analyzerResult: PaymentCardOcrAnalyzer.Prediction,
        val frame: SSDOcr.Input,
        val state: MainLoopState,
    )

    override suspend fun aggregateResult(
        frame: SSDOcr.Input,
        result: PaymentCardOcrAnalyzer.Prediction,
    ): Pair<InterimResult, FinalResult?> {
        val previousState = state
        val currentState = previousState.consumeTransition(result)

        state = currentState

        val interimResult = InterimResult(
            analyzerResult = result,
            frame = frame,
            state = currentState,
        )

        return if (currentState is MainLoopState.Finished) {
            val errors = mutableListOf<String>()
            if (!result.isNameExtractionAvailable && enableNameExtraction) {
                errors.add("name")
            }
            if (!result.isExpiryExtractionAvailable && enableExpiryExtraction) {
                errors.add("expiry")
            }

            val errorString = if (errors.isNotEmpty()) {
                INSUFFICIENT_PERMISSIONS_PREFIX + errors.joinToString(",", prefix = "[", postfix = "]")
            } else null

            interimResult to FinalResult(
                pan = currentState.pan,
                name = currentState.name,
                expiry = currentState.expiry,
                errorString = errorString,
            )
        } else {
            interimResult to null
        }
    }
}
