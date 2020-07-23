package com.getbouncer.cardscan.ui.result

import android.util.Log
import com.getbouncer.cardscan.ui.analyzer.PaymentCardOcrAnalyzer
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.ClockMark
import com.getbouncer.scan.framework.time.seconds
import com.getbouncer.scan.framework.util.ItemCounter
import com.getbouncer.scan.payment.card.isValidPan
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import kotlinx.coroutines.runBlocking
import java.lang.IllegalStateException

private const val DESIRED_PAN_AGREEMENT = 5
private const val DESIRED_NAME_AGREEMENT = 2
private const val MINIMUM_NAME_AGREEMENT = 2
private const val DESIRED_EXPIRY_AGREEMENT = 3
private const val MINIMUM_EXPIRY_AGREEMENT = 3
private val OCR_TIMEOUT_WITH_NAME_AND_EXPIRY = 2.seconds
private val OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY = 5.seconds
private val NAME_AND_EXPIRY_TIMEOUT = 13.seconds

private const val NAME_OR_EXPIRY_UNAVAILABLE_RESPONSE = "<Insufficient API key permissions>"

/**
 * Transitions between states for the main loop.
 */
internal sealed class MainLoopTransition {
    data class FrameWithoutValidPan(val nameExtractionEnabled: Boolean, val expiryExtractionEnabled: Boolean) : MainLoopTransition()
    data class FrameWithValidPan(val pan: String, val nameExtractionEnabled: Boolean, val expiryExtractionEnabled: Boolean) : MainLoopTransition()
    data class FrameWithName(val name: String) : MainLoopTransition()
    data class FrameWithExpiry(val expiry: ExpiryDetect.Expiry) : MainLoopTransition()
    data class FrameWithNameAndExpiry(val name: String, val expiry: ExpiryDetect.Expiry) : MainLoopTransition()
    object FrameWithoutNameOrExpiry : MainLoopTransition()
    object Frame : MainLoopTransition()
}

/**
 * All possible states of the main loop and the logic for transitions to other states.
 */
sealed class MainLoopState(
    val runOcr: Boolean,
    val runNameExtraction: Boolean,
    val runExpiryExtraction: Boolean
) {
    /**
     * Keep track of when this state was reached
     */
    protected val reachedStateAt: ClockMark = Clock.markNow()

    internal abstract suspend fun consumeTransition(transition: MainLoopTransition): MainLoopState

    internal fun handleInvalidTransition(transition: MainLoopTransition): MainLoopState =
        throw IllegalStateException("Invalid transition $transition passed to state $this")

    override fun toString(): String =
        "${this::class.java.simpleName}(runOcr=$runOcr, runNameExtraction=$runNameExtraction, runExpiryExtraction=$runExpiryExtraction, reachedStateAt=$reachedStateAt)"

    init {
        if (Config.isDebug) Log.d(Config.logTag, "Main loop transitioning to state $this")
    }

    /**
     * The initial state of the main loop.
     */
    class Initial : MainLoopState(runOcr = true, runNameExtraction = false, runExpiryExtraction = false) {
        override suspend fun consumeTransition(transition: MainLoopTransition): MainLoopState = when (transition) {
            is MainLoopTransition.FrameWithoutValidPan -> this
            is MainLoopTransition.FrameWithValidPan -> OcrRunning(transition.pan)
            else -> handleInvalidTransition(transition)
        }
    }

    /**
     * The state of the main loop where OCR is running.
     */
    class OcrRunning(firstPan: String) : MainLoopState(runOcr = true, runNameExtraction = false, runExpiryExtraction = false) {
        private val panCounter = ItemCounter<String>().apply { runBlocking { countItem(firstPan) } }

        fun getMostLikelyPan() = panCounter.getHighestCountItem()?.second

        override suspend fun consumeTransition(transition: MainLoopTransition): MainLoopState {
            val (nameExtractionEnabled, expiryExtractionEnabled) = when (transition) {
                is MainLoopTransition.FrameWithoutValidPan -> transition.nameExtractionEnabled to transition.expiryExtractionEnabled
                is MainLoopTransition.FrameWithValidPan -> {
                    panCounter.countItem(transition.pan)
                    transition.nameExtractionEnabled to transition.expiryExtractionEnabled
                }
                else -> {
                    handleInvalidTransition(transition)
                    false to false
                }
            }

            val nameOrExpiryEnabled = nameExtractionEnabled || expiryExtractionEnabled
            val (panAgreements, bestGuessPan) = panCounter.getHighestCountItem() ?: 0 to null
            val timeElapsed = reachedStateAt.elapsedSince()

            return when {
                panAgreements >= DESIRED_PAN_AGREEMENT && bestGuessPan != null ->
                    if (nameExtractionEnabled || expiryExtractionEnabled) {
                        NameAndExpiryRunning(
                            pan = bestGuessPan,
                            nameExtractionEnabled = nameExtractionEnabled,
                            expiryExtractionEnabled = expiryExtractionEnabled
                        )
                    } else {
                        Finished(bestGuessPan, null, null)
                    }
                timeElapsed > OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY && !nameOrExpiryEnabled ->
                    Finished(bestGuessPan ?: "", null, null)
                timeElapsed > OCR_TIMEOUT_WITH_NAME_AND_EXPIRY && nameOrExpiryEnabled ->
                    NameAndExpiryRunning(bestGuessPan ?: "", nameExtractionEnabled, expiryExtractionEnabled)
                else -> this
            }
        }
    }

    /**
     * The state of the main loop where name and expiry extraction are running.
     */
    class NameAndExpiryRunning(
        val pan: String,
        private val nameExtractionEnabled: Boolean,
        private val expiryExtractionEnabled: Boolean
    ) : MainLoopState(runOcr = false, runNameExtraction = nameExtractionEnabled, runExpiryExtraction = expiryExtractionEnabled) {
        private val nameCounter = ItemCounter<String>()
        private val expiryCounter = ItemCounter<ExpiryDetect.Expiry>()

        fun getMostLikelyName() = nameCounter.getHighestCountItem(minCount = MINIMUM_NAME_AGREEMENT)?.second
        fun getMostLikelyExpiry() = expiryCounter.getHighestCountItem(minCount = MINIMUM_EXPIRY_AGREEMENT)?.second

        override suspend fun consumeTransition(transition: MainLoopTransition): MainLoopState {
            when (transition) {
                is MainLoopTransition.FrameWithNameAndExpiry -> {
                    nameCounter.countItem(transition.name)
                    expiryCounter.countItem(transition.expiry)
                }
                is MainLoopTransition.FrameWithName -> nameCounter.countItem(transition.name)
                is MainLoopTransition.FrameWithExpiry -> expiryCounter.countItem(transition.expiry)
                is MainLoopTransition.FrameWithoutNameOrExpiry -> { /* nothing to do */ }
                else -> handleInvalidTransition(transition)
            }

            val (nameAgreements, bestGuessName) = nameCounter.getHighestCountItem(minCount = MINIMUM_NAME_AGREEMENT) ?: 0 to null
            val (expiryAgreements, bestGuessExpiry) = expiryCounter.getHighestCountItem(minCount = MINIMUM_EXPIRY_AGREEMENT) ?: 0 to null

            val nameSatisfied = !nameExtractionEnabled || nameAgreements >= DESIRED_NAME_AGREEMENT
            val expirySatisfied = !expiryExtractionEnabled || expiryAgreements >= DESIRED_EXPIRY_AGREEMENT

            return when {
                nameSatisfied && expirySatisfied -> Finished(pan, bestGuessName, bestGuessExpiry)
                reachedStateAt.elapsedSince() > NAME_AND_EXPIRY_TIMEOUT -> Finished(pan, bestGuessName, bestGuessExpiry)
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
        override suspend fun consumeTransition(transition: MainLoopTransition): MainLoopState = this
    }
}

/**
 * Aggregate results from the main loop. Each frame will trigger an [InterimResult] to the [listener]. Once the
 * [MainLoopState.Finished] state is reached, a [FinalResult] will be sent to the [listener].
 *
 * This aggregator is a state machine. The full list of possible states are subclasses of the [MainLoopState] class. The
 * full list of possible transitions are subclasses of the [MainLoopTransition] class. This was written referencing
 * this article: https://thoughtbot.com/blog/finite-state-machines-android-kotlin-good-times
 */
class MainLoopAggregator(
    listener: AggregateResultListener<InterimResult, FinalResult>,
    private val isNameExtractionEnabled: Boolean = false,
    private val isExpiryExtractionEnabled: Boolean = false
) : ResultAggregator<SSDOcr.Input, MainLoopState, PaymentCardOcrAnalyzer.Prediction, MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult>(
    listener = listener,
    initialState = MainLoopState.Initial()
) {
    data class FinalResult(
        val pan: String?,
        val name: String?,
        val expiry: ExpiryDetect.Expiry?,
        val errorString: String?
    )

    data class InterimResult(
        val analyzerResult: PaymentCardOcrAnalyzer.Prediction,
        val mostLikelyPan: String?,
        val mostLikelyExpiry: ExpiryDetect.Expiry?,
        val mostLikelyName: String?,
        val hasValidPan: Boolean,
        val frame: SSDOcr.Input,
        val state: MainLoopState
    )

    override val name: String = "cardscan_main_loop_aggregator"

    private fun handleOcrStates(result: PaymentCardOcrAnalyzer.Prediction): MainLoopTransition =
        if (isValidPan(result.pan) && result.pan != null) {
            MainLoopTransition.FrameWithValidPan(
                pan = result.pan,
                nameExtractionEnabled = isNameExtractionEnabled && result.isNameAndExpiryExtractionAvailable,
                expiryExtractionEnabled = isExpiryExtractionEnabled && result.isNameAndExpiryExtractionAvailable
            )
        } else {
            MainLoopTransition.FrameWithoutValidPan(
                nameExtractionEnabled = isNameExtractionEnabled && result.isNameAndExpiryExtractionAvailable,
                expiryExtractionEnabled = isExpiryExtractionEnabled && result.isNameAndExpiryExtractionAvailable
            )
        }

    private fun handleNameAndExpiryRunningState(result: PaymentCardOcrAnalyzer.Prediction): MainLoopTransition = when {
        result.name?.isNotEmpty() == true && result.expiry != null -> MainLoopTransition.FrameWithNameAndExpiry(result.name, result.expiry)
        result.name?.isNotEmpty() == true -> MainLoopTransition.FrameWithName(result.name)
        result.expiry != null -> MainLoopTransition.FrameWithExpiry(result.expiry)
        else -> MainLoopTransition.FrameWithoutNameOrExpiry
    }

    override suspend fun aggregateResult(
        frame: SSDOcr.Input,
        result: PaymentCardOcrAnalyzer.Prediction
    ): Pair<InterimResult, FinalResult?> {
        val previousState = state
        val currentState = previousState.consumeTransition(
            when (previousState) {
                is MainLoopState.Initial, is MainLoopState.OcrRunning -> handleOcrStates(result)
                is MainLoopState.NameAndExpiryRunning -> handleNameAndExpiryRunningState(result)
                is MainLoopState.Finished -> MainLoopTransition.Frame
            }
        )

        state = currentState

        val (pan, name, expiry) = when (currentState) {
            is MainLoopState.Initial -> Triple(null, null, null)
            is MainLoopState.OcrRunning -> Triple(currentState.getMostLikelyPan(), null, null)
            is MainLoopState.NameAndExpiryRunning -> Triple(currentState.pan, currentState.getMostLikelyName(), currentState.getMostLikelyExpiry())
            is MainLoopState.Finished -> Triple(currentState.pan, currentState.name, currentState.expiry)
        }

        val interimResult = InterimResult(
            analyzerResult = result,
            mostLikelyPan = pan,
            mostLikelyExpiry = expiry,
            mostLikelyName = name,
            hasValidPan = isValidPan(pan),
            frame = frame,
            state = currentState
        )

        return if (currentState is MainLoopState.Finished) {
            val errorString = if (!result.isNameAndExpiryExtractionAvailable && (isNameExtractionEnabled || isExpiryExtractionEnabled)) {
                NAME_OR_EXPIRY_UNAVAILABLE_RESPONSE
            } else null

            interimResult to FinalResult(
                pan = currentState.pan,
                name = currentState.name,
                expiry = currentState.expiry,
                errorString = errorString
            )
        } else {
            interimResult to null
        }
    }
}
