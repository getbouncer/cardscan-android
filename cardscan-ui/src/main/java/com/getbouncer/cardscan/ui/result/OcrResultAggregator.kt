package com.getbouncer.cardscan.ui.result

import com.getbouncer.cardscan.ui.analyzer.PaymentCardOcrAnalyzer
import com.getbouncer.cardscan.ui.analyzer.PaymentCardOcrState
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.framework.ResultAggregatorConfig
import com.getbouncer.scan.framework.ResultCounter
import com.getbouncer.scan.payment.card.isValidPan
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import kotlinx.coroutines.runBlocking

data class PaymentCardOcrResult(
    val pan: String?,
    val name: String?,
    val expiry: ExpiryDetect.Expiry?,
    val errorString: String?
)

/**
 * Keep track of the results from the [AnalyzerLoop]. Count the number of times the loop sends each
 * PAN as a result, and when the first result is received.
 *
 * The [listener] will be notified of a result once [requiredPanAgreementCount] matching pan results are
 * received and [requiredNameAgreementCount] matching name results are received, or the time since the first result
 * exceeds the [ResultAggregatorConfig.maxTotalAggregationTime].
 */
class OcrResultAggregator(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<SSDOcr.Input, PaymentCardOcrState, InterimResult, PaymentCardOcrResult>,
    private val requiredPanAgreementCount: Int? = null,
    private val requiredNameAgreementCount: Int? = null,
    private val requiredExpiryAgreementCount: Int? = null,
    private val isNameExtractionEnabled: Boolean = false,
    private val isExpiryExtractionEnabled: Boolean = false,
    initialState: PaymentCardOcrState
) : ResultAggregator<SSDOcr.Input, PaymentCardOcrState, PaymentCardOcrAnalyzer.Prediction, OcrResultAggregator.InterimResult, PaymentCardOcrResult>(
    config = config,
    listener = listener,
    initialState = initialState
) {

    data class InterimResult(
        val analyzerResult: PaymentCardOcrAnalyzer.Prediction,
        val mostLikelyPan: String?,
        val mostLikelyName: String?,
        val hasValidPan: Boolean
    )

    companion object {
        const val NAME_OR_EXPIRY_UNAVAILABLE_RESPONSE = "<Insufficient API key permissions>"
    }

    override val name: String = "ocr_result_aggregator"

    private val panResults = ResultCounter<String>()
    private val nameResults = ResultCounter<String>()
    private val expiryResults = ResultCounter<ExpiryDetect.Expiry>()

    private var isPanScanningComplete: Boolean = false
    private var isNameFound: Boolean = false
    private var isExpiryFound: Boolean = false

    override fun reset() {
        super.reset()
        runBlocking {
            panResults.reset()
            nameResults.reset()
        }
    }

    override suspend fun aggregateResult(
        result: PaymentCardOcrAnalyzer.Prediction,
        startAggregationTimer: () -> Unit,
        mustReturnFinal: Boolean
    ): Pair<InterimResult, PaymentCardOcrResult?> {

        val interimResult = InterimResult(
            analyzerResult = result,
            mostLikelyPan = panResults.getMostLikelyResult(),
            mostLikelyName = nameResults.getMostLikelyResult(minCount = 2),
            hasValidPan = isValidPan(result.pan)
        )

        updatePanState(result, startAggregationTimer)
        updateNameState(result.name)
        updateExpiryState(result.expiry)

        val isNameExtractionAvailable = isNameExtractionEnabled && result.isNameAndExpiryExtractionAvailable
        val isExpiryExtractionAvailable = isExpiryExtractionEnabled && result.isNameAndExpiryExtractionAvailable

        val isAggregationComplete = isPanScanningComplete && (!isNameExtractionAvailable || isNameFound) && (!isExpiryExtractionAvailable || isExpiryFound)

        return if (mustReturnFinal || isAggregationComplete) {
            val finalName = if (!result.isNameAndExpiryExtractionAvailable && isNameExtractionEnabled) {
                null
            } else {
                nameResults.getMostLikelyResult(minCount = 2)
            }

            val errorString = if (!result.isNameAndExpiryExtractionAvailable && (isNameExtractionEnabled || isExpiryExtractionEnabled)) {
                NAME_OR_EXPIRY_UNAVAILABLE_RESPONSE
            } else null

            interimResult to PaymentCardOcrResult(
                panResults.getMostLikelyResult(),
                finalName,
                expiry = expiryResults.getMostLikelyResult(minCount = 2),
                errorString = errorString
            )
        } else {
            interimResult to null
        }
    }

    private suspend fun updatePanState(
        result: PaymentCardOcrAnalyzer.Prediction,
        startAggregationTimer: () -> Unit
    ) {
        val pan = result.pan
        val numberCount = if (pan != null && isValidPan(result.pan)) {
            startAggregationTimer()
            panResults.countResult(pan) // This must be last so numberCount is assigned.
        } else 0

        if (requiredPanAgreementCount != null && numberCount >= requiredPanAgreementCount) {
            isPanScanningComplete = true
            state = state.copy(
                runOcr = false,
                runNameExtraction = isNameExtractionEnabled,
                runExpiryExtraction = isExpiryExtractionEnabled
            )
        }
    }

    /**
     * Updates internal counter for expiry, and associated states for finishing the aggregator
     */
    private suspend fun updateNameState(name: String?) {
        val nameCount = if (name?.isNotEmpty() == true) {
            nameResults.countResult(name)
        } else 0

        if (requiredNameAgreementCount != null && nameCount >= requiredNameAgreementCount) {
            isNameFound = true
        }
    }

    /**
     * Updates internal counter for expiry, and associated states for finishing the aggregator
     */
    private suspend fun updateExpiryState(expiry: ExpiryDetect.Expiry?) {
        val expiryCount = if (expiry != null) {
            expiryResults.countResult(expiry)
        } else {
            0
        }
        if (requiredExpiryAgreementCount != null && expiryCount >= requiredExpiryAgreementCount) {
            isExpiryFound = true
        }
    }

    /**
     * Do not save frames for cardscan
     */
    override fun getSaveFrameIdentifier(result: InterimResult, frame: SSDOcr.Input): String? = null

    override fun getFrameSizeBytes(frame: SSDOcr.Input): Int = frame.fullImage.byteCount
}
