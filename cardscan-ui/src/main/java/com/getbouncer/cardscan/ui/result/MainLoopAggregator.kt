package com.getbouncer.cardscan.ui.result

import android.util.Log
import androidx.annotation.Keep
import com.getbouncer.cardscan.ui.SavedFrame
import com.getbouncer.cardscan.ui.SavedFrameType
import com.getbouncer.cardscan.ui.analyzer.MainLoopAnalyzer
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.framework.time.Rate
import com.getbouncer.scan.framework.util.FrameSaver
import com.getbouncer.scan.payment.FrameDetails
import com.getbouncer.scan.payment.card.isValidPan
import kotlinx.coroutines.runBlocking

private const val MAX_SAVED_FRAMES_PER_TYPE = 6

/**
 * Aggregate results from the main loop. Each frame will trigger an [InterimResult] to the [listener]. Once the
 * [MainLoopState.Finished] state is reached, a [FinalResult] will be sent to the [listener].
 *
 * This aggregator is a state machine. The full list of possible states are subclasses of [MainLoopState]. This was
 * written referencing this article: https://thoughtbot.com/blog/finite-state-machines-android-kotlin-good-times
 */
class MainLoopAggregator(
    listener: AggregateResultListener<InterimResult, FinalResult>,
) : ResultAggregator<MainLoopAnalyzer.Input, MainLoopState, MainLoopAnalyzer.Prediction, MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult>(
    listener = listener,
    initialState = MainLoopState.Initial()
) {

    @Keep
    data class FinalResult(
        val pan: String,
        val savedFrames: Map<SavedFrameType, List<SavedFrame>>,
        val averageFrameRate: Rate,
    )

    @Keep
    data class InterimResult(
        val analyzerResult: MainLoopAnalyzer.Prediction,
        val frame: MainLoopAnalyzer.Input,
        val state: MainLoopState,
    )

    private val frameSaver = object : FrameSaver<SavedFrameType, SavedFrame, InterimResult>() {
        override fun getMaxSavedFrames(savedFrameIdentifier: SavedFrameType): Int =
            MAX_SAVED_FRAMES_PER_TYPE
        override fun getSaveFrameIdentifier(frame: SavedFrame, metaData: InterimResult): SavedFrameType? {
            val hasCard = metaData.analyzerResult.isCardVisible == true
            val hasPan = isValidPan(metaData.analyzerResult.ocr?.pan)
            return if (hasCard || hasPan) SavedFrameType(hasCard = hasCard, hasPan = hasPan) else null
        }
    }

    override suspend fun aggregateResult(
        frame: MainLoopAnalyzer.Input,
        result: MainLoopAnalyzer.Prediction
    ): Pair<InterimResult, FinalResult?> {
        val previousState = state
        val currentState = previousState.consumeTransition(result)

        state = currentState

        val interimResult = InterimResult(
            analyzerResult = result,
            frame = frame,
            state = currentState,
        )

        val mostLikelyPan = when (currentState) {
            is MainLoopState.Initial -> null
            is MainLoopState.PanFound -> currentState.getMostLikelyPan()
            is MainLoopState.PanSatisfied -> currentState.pan
            is MainLoopState.CardSatisfied -> currentState.getMostLikelyPan()
            is MainLoopState.Finished -> currentState.pan
        }

        val savedFrame = SavedFrame(
            pan = result.ocr?.pan ?: mostLikelyPan,
            frame = frame,
            details = FrameDetails(
                hasPan = isValidPan(result.ocr?.pan),
                panSideConfidence = result.card?.panProbability ?: 0F,
                noPanSideConfidence = result.card?.noPanProbability ?: 0F,
                noCardConfidence = result.card?.noCardProbability ?: 0F,
            ),
        )

        frame.cameraPreviewImage.image.tracker.trackResult("main_loop_aggregated")
        if (Config.isDebug) {
            Log.d(Config.logTag, "Delay between capture and process of image is ${frame.cameraPreviewImage.image.tracker.startedAt.elapsedSince()}")
        }

        frameSaver.saveFrame(savedFrame, interimResult)

        return if (currentState is MainLoopState.Finished) {
            val savedFrames = frameSaver.getSavedFrames()
            frameSaver.reset()
            interimResult to FinalResult(
                pan = currentState.pan,
                savedFrames = savedFrames,
                averageFrameRate = frameRateTracker.getAverageFrameRate(),
            )
        } else {
            interimResult to null
        }
    }

    override fun reset() {
        super.reset()
        runBlocking { frameSaver.reset() }
    }
}
