package com.getbouncer.cardscan.ui.analyzer

import com.getbouncer.cardscan.ui.result.MainLoopState
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.payment.carddetect.CardDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

class MainLoopAnalyzer(
    private val ssdOcr: Analyzer<SSDOcr.Input, Any, SSDOcr.Prediction>?,
    private val cardDetect: Analyzer<CardDetect.Input, Any, CardDetect.Prediction>?,
) : Analyzer<SSDOcr.Input, MainLoopState, MainLoopAnalyzer.Prediction> {

    private fun SSDOcr.Input.toCardDetectInput() = CardDetect.Input(
        fullImage = fullImage,
        previewSize = previewSize,
        cardFinder = cardFinder,
    )

    class Prediction(
        val ocr: SSDOcr.Prediction?,
        val card: CardDetect.Prediction?,
    ) {
        val isCardVisible = card?.side?.let { it == CardDetect.Prediction.Side.NO_PAN || it == CardDetect.Prediction.Side.PAN }
    }

    override suspend fun analyze(data: SSDOcr.Input, state: MainLoopState): Prediction = supervisorScope {
        val ocrDeferred = if (state.runOcr) async { ssdOcr?.analyze(data, Unit) } else null
        val cardDeferred = if (state.runCardDetect) async { cardDetect?.analyze(data.toCardDetectInput(), Unit) } else null

        Prediction(
            ocr = ocrDeferred?.await(),
            card = cardDeferred?.await(),
        )
    }

    class Factory(
        private val ssdOcrFactory: AnalyzerFactory<SSDOcr.Input, out Any, SSDOcr.Prediction, out Analyzer<SSDOcr.Input, Any, SSDOcr.Prediction>>,
        private val cardDetectFactory: AnalyzerFactory<CardDetect.Input, out Any, CardDetect.Prediction, out Analyzer<CardDetect.Input, Any, CardDetect.Prediction>>,
    ) : AnalyzerFactory<SSDOcr.Input, MainLoopState, Prediction, MainLoopAnalyzer> {
        override suspend fun newInstance(): MainLoopAnalyzer = MainLoopAnalyzer(
            ssdOcr = ssdOcrFactory.newInstance(),
            cardDetect = cardDetectFactory.newInstance(),
        )
    }
}
