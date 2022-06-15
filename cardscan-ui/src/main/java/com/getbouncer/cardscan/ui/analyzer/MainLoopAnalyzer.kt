package com.getbouncer.cardscan.ui.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import com.getbouncer.cardscan.ui.result.MainLoopState
import com.getbouncer.scan.camera.CameraPreviewImage
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.payment.ml.CardDetect
import com.getbouncer.scan.payment.ml.SSDOcr

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
class MainLoopAnalyzer(
    private val ssdOcr: Analyzer<SSDOcr.Input, Any, SSDOcr.Prediction>?,
    private val cardDetect: Analyzer<CardDetect.Input, Any, CardDetect.Prediction>?,
) : Analyzer<MainLoopAnalyzer.Input, MainLoopState, MainLoopAnalyzer.Prediction> {

    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    data class Input(
        val cameraPreviewImage: CameraPreviewImage<Bitmap>,
        val cardFinder: Rect,
    )

    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    class Prediction(
        val ocr: SSDOcr.Prediction?,
        val card: CardDetect.Prediction?,
    ) {
        val isCardVisible = card?.side?.let { it == CardDetect.Prediction.Side.NO_PAN || it == CardDetect.Prediction.Side.PAN }
    }

    override suspend fun analyze(data: Input, state: MainLoopState): Prediction {
        val cardResult = if (state.runCardDetect) cardDetect?.analyze(CardDetect.cameraPreviewToInput(data.cameraPreviewImage.image, data.cameraPreviewImage.previewImageBounds, data.cardFinder), Unit) else null
        val ocrResult = if (state.runOcr) ssdOcr?.analyze(SSDOcr.cameraPreviewToInput(data.cameraPreviewImage.image, data.cameraPreviewImage.previewImageBounds, data.cardFinder), Unit) else null

        return Prediction(
            ocr = ocrResult,
            card = cardResult,
        )
    }

    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    class Factory(
        private val ssdOcrFactory: AnalyzerFactory<SSDOcr.Input, out Any, SSDOcr.Prediction, out Analyzer<SSDOcr.Input, Any, SSDOcr.Prediction>>,
        private val cardDetectFactory: AnalyzerFactory<CardDetect.Input, out Any, CardDetect.Prediction, out Analyzer<CardDetect.Input, Any, CardDetect.Prediction>>,
    ) : AnalyzerFactory<Input, MainLoopState, Prediction, MainLoopAnalyzer> {
        override suspend fun newInstance(): MainLoopAnalyzer = MainLoopAnalyzer(
            ssdOcr = ssdOcrFactory.newInstance(),
            cardDetect = cardDetectFactory.newInstance(),
        )
    }
}
