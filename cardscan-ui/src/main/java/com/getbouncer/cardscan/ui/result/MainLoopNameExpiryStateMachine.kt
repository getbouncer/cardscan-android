package com.getbouncer.cardscan.ui.result

import androidx.annotation.VisibleForTesting
import com.getbouncer.cardscan.ui.analyzer.MainLoopNameExpiryAnalyzer
import com.getbouncer.scan.framework.MachineState
import com.getbouncer.scan.framework.time.seconds
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.ml.ExpiryDetect

@VisibleForTesting
internal val EXTRACT_EXPIRY_DURATION = 3.seconds

@VisibleForTesting
internal val EXTRACT_NAME_DURATION = 11.seconds

@VisibleForTesting
internal const val MINIMUM_NAME_AGREEMENT = 2

@VisibleForTesting
internal const val MINIMUM_EXPIRY_AGREEMENT = 3

@VisibleForTesting
internal const val DESIRED_NAME_AGREEMENT = 3

@VisibleForTesting
internal const val DESIRED_EXPIRY_AGREEMENT = 4

sealed class MainLoopNameExpiryState(
    override val runNameExtraction: Boolean,
    override val runExpiryExtraction: Boolean,
) : MachineState(), NameAndExpiryAnalyzer.State {

    internal abstract suspend fun consumeTransition(
        transition: MainLoopNameExpiryAnalyzer.Prediction
    ): MainLoopNameExpiryState

    /**
     * The state of the main loop where name and expiry extraction are running.
     */
    open class NameAndExpiryRunning(
        private val enableNameExtraction: Boolean,
        private val enableExpiryExtraction: Boolean,
    ) : MainLoopNameExpiryState(
        runNameExtraction = enableNameExtraction,
        runExpiryExtraction = enableExpiryExtraction
    ) {
        protected open val nameCounter = ItemTotalCounter<String>()
        protected open val expiryCounter = ItemTotalCounter<ExpiryDetect.Expiry>()

        fun getMostLikelyName() = nameCounter.getHighestCountItem(minCount = MINIMUM_NAME_AGREEMENT)?.second
        fun getMostLikelyExpiry() = expiryCounter.getHighestCountItem(minCount = MINIMUM_EXPIRY_AGREEMENT)?.second

        override suspend fun consumeTransition(
            transition: MainLoopNameExpiryAnalyzer.Prediction,
        ): MainLoopNameExpiryState {
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
                nameSatisfied && expirySatisfied ->
                    Finished(mostLikelyName, mostLikelyExpiry)
                !enableNameExtraction && mostLikelyName == null && reachedStateAt.elapsedSince() > EXTRACT_EXPIRY_DURATION ->
                    Finished(mostLikelyName, mostLikelyExpiry)
                reachedStateAt.elapsedSince() > EXTRACT_NAME_DURATION ->
                    Finished(mostLikelyName, mostLikelyExpiry)
                nameSatisfied && !expirySatisfied ->
                    object : NameAndExpiryRunning(enableNameExtraction = false, enableExpiryExtraction = enableExpiryExtraction) {
                        override val reachedStateAt = this@NameAndExpiryRunning.reachedStateAt
                        override val nameCounter = this@NameAndExpiryRunning.nameCounter
                        override val expiryCounter = this@NameAndExpiryRunning.expiryCounter
                    }
                !nameSatisfied && expirySatisfied ->
                    object : NameAndExpiryRunning(enableNameExtraction = enableNameExtraction, enableExpiryExtraction = false) {
                        override val reachedStateAt = this@NameAndExpiryRunning.reachedStateAt
                        override val nameCounter = this@NameAndExpiryRunning.nameCounter
                        override val expiryCounter = this@NameAndExpiryRunning.expiryCounter
                    }
                else -> this
            }
        }
    }

    class Finished(
        val name: String?,
        val expiry: ExpiryDetect.Expiry?,
    ) : MainLoopNameExpiryState(runNameExtraction = false, runExpiryExtraction = false) {
        override suspend fun consumeTransition(
            transition: MainLoopNameExpiryAnalyzer.Prediction,
        ): MainLoopNameExpiryState = this
    }
}
