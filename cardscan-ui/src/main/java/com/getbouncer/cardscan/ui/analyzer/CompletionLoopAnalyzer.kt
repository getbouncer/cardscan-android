package com.getbouncer.cardscan.ui.analyzer

import com.getbouncer.cardscan.ui.SavedFrame
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.carddetect.CardDetect

class CompletionLoopAnalyzer private constructor(
    private val nameAndExpiryAnalyzer: NameAndExpiryAnalyzer?,
) : Analyzer<SavedFrame, Unit, CompletionLoopAnalyzer.Prediction> {
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
            NameAndExpiryAnalyzer.Input(
                squareImage = TrackedImage(
                    CardDetect.cropCameraPreviewForCardDetect(
                        cameraPreviewImage = data.frame.cameraPreviewImage.image,
                        previewSize = data.frame.previewSize,
                        cardFinder = data.frame.cardFinder,
                    ),
                    data.frame.cameraPreviewImage.tracker,
                ),
            ),
            state,
        ),
        isNameExtractionAvailable = nameAndExpiryAnalyzer?.isNameDetectorAvailable() ?: false,
        isExpiryExtractionAvailable = nameAndExpiryAnalyzer?.isExpiryDetectorAvailable() ?: false,
        enableNameExtraction = nameAndExpiryAnalyzer?.runNameExtraction ?: false,
        enableExpiryExtraction = nameAndExpiryAnalyzer?.runExpiryExtraction ?: false,
    )

    class Factory(
        private val nameAndExpiryFactory: AnalyzerFactory<NameAndExpiryAnalyzer.Input, Any, NameAndExpiryAnalyzer.Prediction, out NameAndExpiryAnalyzer>,
    ) : AnalyzerFactory<SavedFrame, Unit, Prediction, CompletionLoopAnalyzer> {
        override suspend fun newInstance() = CompletionLoopAnalyzer(
            nameAndExpiryAnalyzer = nameAndExpiryFactory.newInstance(),
        )
    }
}
