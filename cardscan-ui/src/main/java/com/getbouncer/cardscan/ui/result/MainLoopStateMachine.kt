package com.getbouncer.cardscan.ui.result

import androidx.annotation.VisibleForTesting
import com.getbouncer.cardscan.ui.analyzer.MainLoopAnalyzer
import com.getbouncer.scan.framework.MachineState
import com.getbouncer.scan.framework.time.seconds
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.card.isValidPan

@VisibleForTesting
internal val PAN_SEARCH_DURATION = 5.seconds

@VisibleForTesting
internal val PAN_AND_CARD_SEARCH_DURATION = 10.seconds

@VisibleForTesting
internal val DESIRED_PAN_AGREEMENT = 5

@VisibleForTesting
internal val MINIMUM_PAN_AGREEMENT = 2

@VisibleForTesting
internal val DESIRED_SIDE_COUNT = 8

sealed class MainLoopState(
    val runOcr: Boolean,
    val runCardDetect: Boolean,
) : MachineState() {

    internal abstract suspend fun consumeTransition(
        transition: MainLoopAnalyzer.Prediction,
    ): MainLoopState

    class Initial : MainLoopState(runOcr = true, runCardDetect = false) {
        override suspend fun consumeTransition(
            transition: MainLoopAnalyzer.Prediction,
        ): MainLoopState = when {
            isValidPan(transition.ocr?.pan) ->
                PanFound(ItemTotalCounter(transition.ocr?.pan ?: ""))
            else -> this
        }
    }

    class PanFound(
        private val panCounter: ItemTotalCounter<String>,
    ) : MainLoopState(runOcr = true, runCardDetect = true) {
        private var visibleCardCount = 0

        fun getMostLikelyPan() = panCounter.getHighestCountItem()?.second

        private fun isCardSatisfied() = visibleCardCount >= DESIRED_SIDE_COUNT
        private fun isPanSatisfied() =
            panCounter.getHighestCountItem()?.first ?: 0 >= DESIRED_PAN_AGREEMENT ||
                (
                    panCounter.getHighestCountItem()?.first ?: 0 >= MINIMUM_PAN_AGREEMENT &&
                        reachedStateAt.elapsedSince() > PAN_SEARCH_DURATION
                    )

        override suspend fun consumeTransition(
            transition: MainLoopAnalyzer.Prediction,
        ): MainLoopState {
            if (isValidPan(transition.ocr?.pan)) {
                panCounter.countItem(transition.ocr?.pan ?: "")
            }

            if (transition.isCardVisible == true) {
                visibleCardCount++
            }

            return when {
                reachedStateAt.elapsedSince() > PAN_AND_CARD_SEARCH_DURATION -> Finished(getMostLikelyPan() ?: "")
                isCardSatisfied() && isPanSatisfied() -> Finished(getMostLikelyPan() ?: "")
                isCardSatisfied() -> CardSatisfied(panCounter)
                isPanSatisfied() -> PanSatisfied(getMostLikelyPan() ?: "", visibleCardCount)
                else -> this
            }
        }
    }

    class PanSatisfied(
        val pan: String,
        var visibleCardCount: Int,
    ) : MainLoopState(runOcr = false, runCardDetect = true) {
        private fun isCardSatisfied() = visibleCardCount >= DESIRED_SIDE_COUNT

        override suspend fun consumeTransition(
            transition: MainLoopAnalyzer.Prediction,
        ): MainLoopState {
            if (transition.isCardVisible == true) {
                visibleCardCount++
            }

            return when {
                reachedStateAt.elapsedSince() > PAN_SEARCH_DURATION -> Finished(pan)
                isCardSatisfied() -> Finished(pan)
                else -> this
            }
        }
    }

    class CardSatisfied(
        private val panCounter: ItemTotalCounter<String>,
    ) : MainLoopState(runOcr = true, runCardDetect = false) {
        fun getMostLikelyPan() = panCounter.getHighestCountItem()?.second
        private fun isPanSatisfied() =
            panCounter.getHighestCountItem()?.first ?: 0 >= DESIRED_PAN_AGREEMENT

        override suspend fun consumeTransition(
            transition: MainLoopAnalyzer.Prediction,
        ): MainLoopState {
            if (transition.ocr?.pan != null && isValidPan(transition.ocr.pan)) {
                panCounter.countItem(transition.ocr.pan)
            }

            return when {
                isPanSatisfied() -> Finished(getMostLikelyPan() ?: "")
                reachedStateAt.elapsedSince() >= PAN_SEARCH_DURATION ->
                    Finished(getMostLikelyPan() ?: "")
                else -> this
            }
        }
    }

    class Finished(val pan: String) : MainLoopState(runOcr = false, runCardDetect = false) {
        override suspend fun consumeTransition(
            transition: MainLoopAnalyzer.Prediction,
        ): MainLoopState = this
    }
}
