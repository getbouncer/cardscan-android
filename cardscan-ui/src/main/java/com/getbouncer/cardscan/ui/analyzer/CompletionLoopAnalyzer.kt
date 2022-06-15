package com.getbouncer.cardscan.ui.analyzer

import com.getbouncer.cardscan.ui.SavedFrame
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
class CompletionLoopAnalyzer private constructor(
    private val nameAndExpiryAnalyzer: NameAndExpiryAnalyzer?,
) : Analyzer<SavedFrame, Unit, CompletionLoopAnalyzer.Prediction> {
    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    class Prediction(
        val nameAndExpiryResult: NameAndExpiryAnalyzer.Prediction?,
        val isNameExtractionAvailable: Boolean,
        val isExpiryExtractionAvailable: Boolean,
        val enableNameExtraction: Boolean,
        val enableExpiryExtraction: Boolean,
    )

    override suspend fun analyze(
        data: SavedFrame,
        state: Unit,
    ) = Prediction(
        nameAndExpiryResult = nameAndExpiryAnalyzer?.analyze(
            NameAndExpiryAnalyzer.Input(data.frame.cameraPreviewImage.image, data.frame.cameraPreviewImage.previewImageBounds, data.frame.cardFinder),
            state,
        ),
        isNameExtractionAvailable = nameAndExpiryAnalyzer?.isNameDetectorAvailable() ?: false,
        isExpiryExtractionAvailable = nameAndExpiryAnalyzer?.isExpiryDetectorAvailable() ?: false,
        enableNameExtraction = nameAndExpiryAnalyzer?.runNameExtraction ?: false,
        enableExpiryExtraction = nameAndExpiryAnalyzer?.runExpiryExtraction ?: false,
    )

    @Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
    class Factory(
        private val nameAndExpiryFactory: AnalyzerFactory<NameAndExpiryAnalyzer.Input, Any, NameAndExpiryAnalyzer.Prediction, out NameAndExpiryAnalyzer>,
    ) : AnalyzerFactory<SavedFrame, Unit, Prediction, CompletionLoopAnalyzer> {
        override suspend fun newInstance() = CompletionLoopAnalyzer(
            nameAndExpiryAnalyzer = nameAndExpiryFactory.newInstance(),
        )
    }
}
