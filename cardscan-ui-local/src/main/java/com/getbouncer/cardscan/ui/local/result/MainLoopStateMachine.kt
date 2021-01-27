package com.getbouncer.cardscan.ui.local.result

import androidx.annotation.VisibleForTesting
import com.getbouncer.scan.framework.MachineState
import com.getbouncer.scan.framework.time.seconds
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.card.isValidPan
import com.getbouncer.scan.payment.ml.SSDOcr

@VisibleForTesting
internal val TOTAL_SCAN_DURATION = 10.seconds

@VisibleForTesting
internal val PAN_SEARCH_DURATION = 5.seconds

@VisibleForTesting
internal val DESIRED_PAN_AGREEMENT = 5

@VisibleForTesting
internal val MINIMUM_PAN_AGREEMENT = 2

sealed class MainLoopState : MachineState() {

    internal abstract suspend fun consumeTransition(
        transition: SSDOcr.Prediction,
    ): MainLoopState

    class Initial : MainLoopState() {
        override suspend fun consumeTransition(
            transition: SSDOcr.Prediction,
        ): MainLoopState = when {
            isValidPan(transition.pan) -> PanFound(ItemTotalCounter(transition.pan))
            else -> this
        }
    }

    class PanFound(
        private val panCounter: ItemTotalCounter<String>,
    ) : MainLoopState() {
        fun getMostLikelyPan() = panCounter.getHighestCountItem()?.second

        private fun isPanSatisfied() =
            panCounter.getHighestCountItem()?.first ?: 0 >= DESIRED_PAN_AGREEMENT ||
                (
                    panCounter.getHighestCountItem()?.first ?: 0 >= MINIMUM_PAN_AGREEMENT &&
                        reachedStateAt.elapsedSince() > PAN_SEARCH_DURATION
                    )

        override suspend fun consumeTransition(
            transition: SSDOcr.Prediction,
        ): MainLoopState {
            if (isValidPan(transition.pan)) {
                panCounter.countItem(transition.pan)
            }

            return when {
                reachedStateAt.elapsedSince() > TOTAL_SCAN_DURATION -> Finished(getMostLikelyPan() ?: "")
                isPanSatisfied() -> Finished(getMostLikelyPan() ?: "")
                else -> this
            }
        }
    }

    class Finished(val pan: String) : MainLoopState() {
        override suspend fun consumeTransition(
            transition: SSDOcr.Prediction,
        ): MainLoopState = this
    }
}
