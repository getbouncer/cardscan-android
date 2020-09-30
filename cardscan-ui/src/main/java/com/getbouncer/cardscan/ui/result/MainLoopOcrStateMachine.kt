package com.getbouncer.cardscan.ui.result

import androidx.annotation.VisibleForTesting
import com.getbouncer.scan.framework.MachineState
import com.getbouncer.scan.framework.time.seconds
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.card.isValidPan
import com.getbouncer.scan.payment.ml.SSDOcr

@VisibleForTesting
internal const val DESIRED_PAN_AGREEMENT = 5

@VisibleForTesting
internal val OCR_TIMEOUT_WITH_NAME_AND_EXPIRY = 2.seconds

@VisibleForTesting
internal val OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY = 5.seconds

/**
 * All possible states of the main loop and the logic for transitions to other states.
 */
sealed class MainLoopOcrState : MachineState() {

    internal abstract suspend fun consumeTransition(
        transition: SSDOcr.Prediction
    ): MainLoopOcrState

    /**
     * The initial state of the main loop.
     */
    class Initial(private val enableNameExpiryExtraction: Boolean) : MainLoopOcrState() {
        override suspend fun consumeTransition(
            transition: SSDOcr.Prediction,
        ): MainLoopOcrState = when {
            isValidPan(transition.pan) -> OcrRunning(transition.pan, enableNameExpiryExtraction)
            else -> this
        }
    }

    /**
     * The state of the main loop where OCR is running.
     */
    class OcrRunning(
        firstPan: String,
        private val enableNameExpiryExtraction: Boolean,
    ) : MainLoopOcrState() {
        private val panCounter = ItemTotalCounter(firstPan)

        fun getMostLikelyPan() = panCounter.getHighestCountItem()?.second

        override suspend fun consumeTransition(transition: SSDOcr.Prediction): MainLoopOcrState {
            if (isValidPan(transition.pan)) {
                panCounter.countItem(transition.pan)
            }

            val (panAgreements, mostLikelyPan) = panCounter.getHighestCountItem() ?: 0 to null
            val timeElapsed = reachedStateAt.elapsedSince()

            return when {
                panAgreements >= DESIRED_PAN_AGREEMENT && mostLikelyPan != null ->
                    Finished(mostLikelyPan)
                timeElapsed > OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY && !enableNameExpiryExtraction ->
                    Finished(mostLikelyPan ?: "")
                timeElapsed > OCR_TIMEOUT_WITH_NAME_AND_EXPIRY && enableNameExpiryExtraction ->
                    Finished(mostLikelyPan ?: "")
                else -> this
            }
        }
    }

    /**
     * The final state of the state machine.
     */
    class Finished(val pan: String) : MainLoopOcrState() {
        override suspend fun consumeTransition(transition: SSDOcr.Prediction) = this
    }
}
