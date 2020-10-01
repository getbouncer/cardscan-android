package com.getbouncer.cardscan.ui.analyzer

import com.getbouncer.cardscan.ui.result.MainLoopOcrState
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.payment.ml.SSDOcr

class MainLoopOcrAnalyzer private constructor(
    private val ssdOcrAnalyzer: Analyzer<SSDOcr.Input, Unit, SSDOcr.Prediction>,
) : Analyzer<SSDOcr.Input, MainLoopOcrState, SSDOcr.Prediction> {

    override suspend fun analyze(data: SSDOcr.Input, state: MainLoopOcrState): SSDOcr.Prediction =
        ssdOcrAnalyzer.analyze(data, Unit)

    class Factory(
        private val ssdOcrAnalyzerFactory: AnalyzerFactory<SSDOcr.Input, Unit, SSDOcr.Prediction, out Analyzer<SSDOcr.Input, Unit, SSDOcr.Prediction>>
    ) : AnalyzerFactory<SSDOcr.Input, MainLoopOcrState, SSDOcr.Prediction, MainLoopOcrAnalyzer> {
        override suspend fun newInstance(): MainLoopOcrAnalyzer? =
            ssdOcrAnalyzerFactory.newInstance()?.let { MainLoopOcrAnalyzer(it) }
    }
}
