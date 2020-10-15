package com.getbouncer.cardscan.ui.result

import android.util.Log
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.framework.time.Rate
import com.getbouncer.scan.payment.ml.SSDOcr

/**
 * Aggregate results from the main loop. Each frame will trigger an [InterimResult] to the
 * [listener]. Once the [MainLoopOcrState.Finished] state is reached, a [FinalResult] will be sent
 * to the [listener].
 *
 * This aggregator is a state machine. The full list of possible states are subclasses of the
 * [MainLoopOcrState] class. This was written referencing this article:
 * https://thoughtbot.com/blog/finite-state-machines-android-kotlin-good-times
 */
class MainLoopOcrAggregator(
    listener: AggregateResultListener<InterimResult, FinalResult>,
    enableNameExpiryExtraction: Boolean = false,
) : ResultAggregator<SSDOcr.Input, MainLoopOcrState, SSDOcr.Prediction, MainLoopOcrAggregator.InterimResult, MainLoopOcrAggregator.FinalResult>(
    listener = listener,
    initialState = MainLoopOcrState.Initial(enableNameExpiryExtraction = enableNameExpiryExtraction)
) {
    data class InterimResult(
        val analyzerResult: SSDOcr.Prediction,
        val frame: SSDOcr.Input,
        val state: MainLoopOcrState,
    )

    data class FinalResult(
        val pan: String,
        val averageFrameRate: Rate,
    )

    override suspend fun aggregateResult(
        frame: SSDOcr.Input,
        result: SSDOcr.Prediction,
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

        return if (currentState is MainLoopOcrState.Finished) {
            interimResult to FinalResult(
                pan = currentState.pan,
                averageFrameRate = frameRateTracker.getAverageFrameRate(),
            )
        } else {
            interimResult to null
        }
    }
}
