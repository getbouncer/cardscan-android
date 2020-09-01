package com.getbouncer.cardscan.ui.analyzer

import com.getbouncer.cardscan.ui.result.MainLoopState
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.ssd.DetectionBox
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class PaymentCardOcrAnalyzer private constructor(
    private val ssdOcr: SSDOcr?,
    private val nameAndExpiryAnalyzer: NameAndExpiryAnalyzer<MainLoopState>?
) : Analyzer<SSDOcr.Input, MainLoopState, PaymentCardOcrAnalyzer.Prediction> {

    data class Prediction(
        val pan: String?,
        val panDetectionBoxes: List<DetectionBox>?,
        val name: String?,
        val expiry: ExpiryDetect.Expiry?,
        val objDetectionBoxes: List<DetectionBox>?,
        val isExpiryExtractionAvailable: Boolean,
        val isNameExtractionAvailable: Boolean
    )

    override suspend fun analyze(data: SSDOcr.Input, state: MainLoopState) = supervisorScope {
        val nameAndExpiryDeferred = if ((state.runNameExtraction || state.runExpiryExtraction) && nameAndExpiryAnalyzer != null) {
            this.async {
                nameAndExpiryAnalyzer.analyze(data, state)
            }
        } else {
            null
        }

        val ocrDeferred = if (state.runOcr && ssdOcr != null) {
            this.async {
                ssdOcr.analyze(data, Unit)
            }
        } else {
            null
        }

        Prediction(
            pan = ocrDeferred?.await()?.pan,
            panDetectionBoxes = ocrDeferred?.await()?.detectedBoxes,
            name = nameAndExpiryDeferred?.await()?.name,
            expiry = nameAndExpiryDeferred?.await()?.expiry,
            objDetectionBoxes = nameAndExpiryDeferred?.await()?.boxes,
            isExpiryExtractionAvailable = nameAndExpiryAnalyzer?.isExpiryDetectorAvailable() ?: false,
            isNameExtractionAvailable = nameAndExpiryAnalyzer?.isNameDetectorAvailable() ?: false
        )
    }

    class Factory(
        private val ssdOcrFactory: SSDOcr.Factory,
        private val nameDetectFactory: NameAndExpiryAnalyzer.Factory<MainLoopState>?
    ) : AnalyzerFactory<PaymentCardOcrAnalyzer> {
        override suspend fun newInstance(): PaymentCardOcrAnalyzer? = PaymentCardOcrAnalyzer(
            ssdOcrFactory.newInstance(),
            nameDetectFactory?.newInstance()
        )
    }
}
