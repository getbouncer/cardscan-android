package com.getbouncer.cardscan.ui.analyzer

import com.getbouncer.cardscan.ui.result.MainLoopNameExpiryState
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.ssd.DetectionBox
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class MainLoopNameExpiryAnalyzer private constructor(
    private val nameAndExpiryAnalyzer: NameAndExpiryAnalyzer<MainLoopNameExpiryState>,
) : Analyzer<SSDOcr.Input, MainLoopNameExpiryState, MainLoopNameExpiryAnalyzer.Prediction> {

    class Prediction(
        val name: String?,
        val expiry: ExpiryDetect.Expiry?,
        val detectionBoxes: List<DetectionBox>?,
        val isExpiryExtractionAvailable: Boolean,
        val isNameExtractionAvailable: Boolean
    )

    override suspend fun analyze(data: SSDOcr.Input, state: MainLoopNameExpiryState): Prediction = supervisorScope {
        val nameAndExpiryDeferred = if ((state.runNameExtraction || state.runExpiryExtraction)) {
            async { nameAndExpiryAnalyzer.analyze(data, state) }
        } else null

        val nameAndExpiry = nameAndExpiryDeferred?.await()

        Prediction(
            name = nameAndExpiry?.name,
            expiry = nameAndExpiry?.expiry,
            detectionBoxes = nameAndExpiry?.boxes,
            isExpiryExtractionAvailable = nameAndExpiryAnalyzer.isExpiryDetectorAvailable(),
            isNameExtractionAvailable = nameAndExpiryAnalyzer.isNameDetectorAvailable(),
        )
    }

    class Factory(
        private val nameAndExpiryFactory: NameAndExpiryAnalyzer.Factory<MainLoopNameExpiryState>,
    ) : AnalyzerFactory<MainLoopNameExpiryAnalyzer> {
        override suspend fun newInstance(): MainLoopNameExpiryAnalyzer? =
            MainLoopNameExpiryAnalyzer(nameAndExpiryFactory.newInstance())
    }
}
