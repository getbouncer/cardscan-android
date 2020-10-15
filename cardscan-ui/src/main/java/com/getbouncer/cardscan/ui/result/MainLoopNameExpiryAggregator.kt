package com.getbouncer.cardscan.ui.result

import android.util.Log
import com.getbouncer.cardscan.ui.analyzer.MainLoopNameExpiryAnalyzer
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr

private const val INSUFFICIENT_PERMISSIONS_PREFIX = "Insufficient API key permissions - "

/**
 * Aggregate results from the main loop. Each frame will trigger an [InterimResult] to the
 * [listener]. Once the [MainLoopNameExpiryState.Finished] state is reached, a [String] will be sent
 * to the [listener].
 *
 * This aggregator is a state machine. The full list of possible states are subclasses of the
 * [MainLoopOcrState] class. This was written referencing this article:
 * https://thoughtbot.com/blog/finite-state-machines-android-kotlin-good-times
 */
class MainLoopNameExpiryAggregator(
    listener: AggregateResultListener<InterimResult, FinalResult>,
    private val enableNameExtraction: Boolean = false,
    private val enableExpiryExtraction: Boolean = false,
) : ResultAggregator<SSDOcr.Input, MainLoopNameExpiryState, MainLoopNameExpiryAnalyzer.Prediction, MainLoopNameExpiryAggregator.InterimResult, MainLoopNameExpiryAggregator.FinalResult>(
    listener = listener,
    initialState = MainLoopNameExpiryState.NameAndExpiryRunning(
        enableNameExtraction = enableNameExtraction,
        enableExpiryExtraction = enableExpiryExtraction,
    )
) {
    data class FinalResult(
        val name: String?,
        val expiry: ExpiryDetect.Expiry?,
        val errorString: String?,
    )

    data class InterimResult(
        val analyzerResult: MainLoopNameExpiryAnalyzer.Prediction,
        val frame: SSDOcr.Input,
        val state: MainLoopNameExpiryState,
    )

    override suspend fun aggregateResult(
        frame: SSDOcr.Input,
        result: MainLoopNameExpiryAnalyzer.Prediction,
    ): Pair<InterimResult, FinalResult?> {
        val previousState = state
        val currentState = previousState.consumeTransition(result)

        state = currentState

        val interimResult = InterimResult(
            analyzerResult = result,
            frame = frame,
            state = currentState,
        )

        if (Config.isDebug) {
            Log.d(Config.logTag, "Delay between capture and process of image is ${frame.capturedAt.elapsedSince()}")
        }

        return if (currentState is MainLoopNameExpiryState.Finished) {
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
                name = currentState.name,
                expiry = currentState.expiry,
                errorString = errorString,
            )
        } else {
            interimResult to null
        }
    }
}
