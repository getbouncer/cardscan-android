package com.getbouncer.cardscan.ui.result

import androidx.annotation.VisibleForTesting
import com.getbouncer.cardscan.ui.analyzer.PaymentCardOcrAnalyzer
import com.getbouncer.scan.framework.MachineState
import com.getbouncer.scan.framework.time.seconds
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.card.isValidPan
import com.getbouncer.scan.payment.ml.ExpiryDetect

@VisibleForTesting
internal const val DESIRED_PAN_AGREEMENT = 5

@VisibleForTesting
internal const val DESIRED_NAME_AGREEMENT = 3

@VisibleForTesting
internal const val DESIRED_EXPIRY_AGREEMENT = 4

@VisibleForTesting
internal const val MINIMUM_NAME_AGREEMENT = 2

@VisibleForTesting
internal const val MINIMUM_EXPIRY_AGREEMENT = 3

@VisibleForTesting
internal val OCR_TIMEOUT_WITH_NAME_AND_EXPIRY = 2.seconds

@VisibleForTesting
internal val OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY = 5.seconds

@VisibleForTesting
internal val EXPIRY_TIMEOUT = 3.seconds

@VisibleForTesting
internal val NAME_TIMEOUT = 13.seconds

/**
 * All possible states of the main loop and the logic for transitions to other states.
 */
sealed class MainLoopState(
    val runOcr: Boolean,
    override val runNameExtraction: Boolean,
    override val runExpiryExtraction: Boolean,
) : MachineState(), NameAndExpiryAnalyzer.State {

    internal abstract suspend fun consumeTransition(transition: PaymentCardOcrAnalyzer.Prediction): MainLoopState

    /**
     * The initial state of the main loop.
     */
    class Initial(
        private val enableNameExtraction: Boolean,
        private val enableExpiryExtraction: Boolean,
    ) : MainLoopState(runOcr = true, runNameExtraction = false, runExpiryExtraction = false) {
        override suspend fun consumeTransition(transition: PaymentCardOcrAnalyzer.Prediction): MainLoopState = when {
            isValidPan(transition.pan) && transition.pan != null ->
                OcrRunning(transition.pan, enableNameExtraction, enableExpiryExtraction)
            else -> this
        }
    }

    /**
     * The state of the main loop where OCR is running.
     */
    class OcrRunning(
        firstPan: String,
        private val enableNameExtraction: Boolean,
        private val enableExpiryExtraction: Boolean,
    ) : MainLoopState(runOcr = true, runNameExtraction = false, runExpiryExtraction = false) {
        private val panCounter = ItemTotalCounter(firstPan)

        fun getMostLikelyPan() = panCounter.getHighestCountItem()?.second

        override suspend fun consumeTransition(transition: PaymentCardOcrAnalyzer.Prediction): MainLoopState {
            if (isValidPan(transition.pan) && transition.pan != null) {
                panCounter.countItem(transition.pan)
            }

            val nameOrExpiryEnabled = enableNameExtraction || enableExpiryExtraction
            val (panAgreements, mostLikelyPan) = panCounter.getHighestCountItem() ?: 0 to null
            val timeElapsed = reachedStateAt.elapsedSince()

            return when {
                panAgreements >= DESIRED_PAN_AGREEMENT && mostLikelyPan != null ->
                    if (enableNameExtraction || enableExpiryExtraction) {
                        NameAndExpiryRunning(
                            pan = mostLikelyPan,
                            enableNameExtraction = enableNameExtraction,
                            enableExpiryExtraction = enableExpiryExtraction,
                        )
                    } else {
                        Finished(mostLikelyPan, null, null)
                    }
                timeElapsed > OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY && !nameOrExpiryEnabled ->
                    Finished(mostLikelyPan ?: "", null, null)
                timeElapsed > OCR_TIMEOUT_WITH_NAME_AND_EXPIRY && nameOrExpiryEnabled ->
                    NameAndExpiryRunning(
                        pan = mostLikelyPan ?: "",
                        enableNameExtraction = enableNameExtraction,
                        enableExpiryExtraction = enableExpiryExtraction
                    )
                else -> this
            }
        }
    }

    /**
     * The state of the main loop where name and expiry extraction are running.
     */
    open class NameAndExpiryRunning(
        val pan: String,
        private val enableNameExtraction: Boolean,
        private val enableExpiryExtraction: Boolean,
    ) : MainLoopState(runOcr = false, runNameExtraction = enableNameExtraction, runExpiryExtraction = enableExpiryExtraction) {
        protected open val nameCounter = ItemTotalCounter<String>()
        protected open val expiryCounter = ItemTotalCounter<ExpiryDetect.Expiry>()

        fun getMostLikelyName() = nameCounter.getHighestCountItem(minCount = MINIMUM_NAME_AGREEMENT)?.second
        fun getMostLikelyExpiry() = expiryCounter.getHighestCountItem(minCount = MINIMUM_EXPIRY_AGREEMENT)?.second

        override suspend fun consumeTransition(transition: PaymentCardOcrAnalyzer.Prediction): MainLoopState {
            if (transition.name?.isNotEmpty() == true) {
                nameCounter.countItem(transition.name)
            }

            if (transition.expiry != null) {
                expiryCounter.countItem(transition.expiry)
            }

            val (nameAgreements, mostLikelyName) = nameCounter.getHighestCountItem(minCount = MINIMUM_NAME_AGREEMENT) ?: 0 to null
            val (expiryAgreements, mostLikelyExpiry) = expiryCounter.getHighestCountItem(minCount = MINIMUM_EXPIRY_AGREEMENT) ?: 0 to null

            val nameSatisfied = !enableNameExtraction || !transition.isNameExtractionAvailable || nameAgreements >= DESIRED_NAME_AGREEMENT
            val expirySatisfied = !enableExpiryExtraction || !transition.isExpiryExtractionAvailable || expiryAgreements >= DESIRED_EXPIRY_AGREEMENT

            return when {
                nameSatisfied && expirySatisfied -> Finished(pan, mostLikelyName, mostLikelyExpiry)
                !enableNameExtraction && mostLikelyName == null && reachedStateAt.elapsedSince() > EXPIRY_TIMEOUT -> Finished(pan, mostLikelyName, mostLikelyExpiry)
                reachedStateAt.elapsedSince() > NAME_TIMEOUT -> Finished(pan, mostLikelyName, mostLikelyExpiry)
                nameSatisfied && !expirySatisfied ->
                    object : NameAndExpiryRunning(pan, enableNameExtraction = false, enableExpiryExtraction = enableExpiryExtraction) {
                        override val reachedStateAt = this@NameAndExpiryRunning.reachedStateAt
                        override val nameCounter = this@NameAndExpiryRunning.nameCounter
                        override val expiryCounter = this@NameAndExpiryRunning.expiryCounter
                    }
                !nameSatisfied && expirySatisfied ->
                    object : NameAndExpiryRunning(pan, enableNameExtraction = enableNameExtraction, enableExpiryExtraction = false) {
                        override val reachedStateAt = this@NameAndExpiryRunning.reachedStateAt
                        override val nameCounter = this@NameAndExpiryRunning.nameCounter
                        override val expiryCounter = this@NameAndExpiryRunning.expiryCounter
                    }
                else -> this
            }
        }
    }

    /**
     * The final state of the aggregator.
     */
    class Finished(
        val pan: String,
        val name: String?,
        val expiry: ExpiryDetect.Expiry?
    ) : MainLoopState(runOcr = false, runNameExtraction = false, runExpiryExtraction = false) {
        override suspend fun consumeTransition(transition: PaymentCardOcrAnalyzer.Prediction): MainLoopState = this
    }
}
