package com.getbouncer.cardscan.ui.local.result

import android.util.Log
import androidx.annotation.Keep
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.payment.ml.SSDOcr

/**
 * Aggregate results from the main loop. Each frame will trigger an [InterimResult] to the [listener]. Once the
 * [MainLoopState.Finished] state is reached, the pan will be sent to the [listener].
 *
 * This aggregator is a state machine. The full list of possible states are subclasses of [MainLoopState]. This was
 * written referencing this article: https://thoughtbot.com/blog/finite-state-machines-android-kotlin-good-times
 */
class MainLoopAggregator(
    listener: AggregateResultListener<InterimResult, String>,
) : ResultAggregator<SSDOcr.Input, MainLoopState, SSDOcr.Prediction, MainLoopAggregator.InterimResult, String>(
    listener = listener,
    initialState = MainLoopState.Initial()
) {

    @Keep
    data class InterimResult(
        val analyzerResult: SSDOcr.Prediction,
        val frame: SSDOcr.Input,
        val state: MainLoopState,
    )

    override suspend fun aggregateResult(
        frame: SSDOcr.Input,
        result: SSDOcr.Prediction
    ): Pair<InterimResult, String?> {
        val previousState = state
        val currentState = previousState.consumeTransition(result)

        state = currentState

        val interimResult = InterimResult(
            analyzerResult = result,
            frame = frame,
            state = currentState,
        )

        frame.fullImage.tracker.trackResult("main_loop_aggregated")
        if (Config.isDebug) {
            Log.d(Config.logTag, "Delay between capture and process of image is ${frame.fullImage.tracker.startedAt.elapsedSince()}")
        }

        return if (currentState is MainLoopState.Finished) {
            interimResult to currentState.pan
        } else {
            interimResult to null
        }
    }
}
