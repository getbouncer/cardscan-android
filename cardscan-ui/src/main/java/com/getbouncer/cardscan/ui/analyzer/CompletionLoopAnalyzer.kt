package com.getbouncer.cardscan.ui.analyzer

import com.getbouncer.cardverify.ui.base.SavedFrame
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.card.iin
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.verify.FrameDetails
import com.getbouncer.scan.payment.verify.ml.BobDetect
import com.getbouncer.scan.payment.verify.ml.SSDObjectDetect
import kotlinx.coroutines.async
import kotlinx.coroutines.supervisorScope

fun SSDOcr.Input.toScreenDetectInput(frameDetails: FrameDetails): BobDetect.Input = BobDetect.Input(
    fullImage = fullImage,
    previewSize = previewSize,
    cardFinder = cardFinder,
    frameDetails = frameDetails,
)

fun SSDOcr.Input.toObjectDetectInput(iin: String?, frameDetails: FrameDetails): SSDObjectDetect.Input = SSDObjectDetect.Input(
    fullImage = fullImage,
    previewSize = previewSize,
    cardFinder = cardFinder,
    iin = iin,
    frameDetails = frameDetails,
)

class CompletionLoopAnalyzer private constructor(
    private val pan: String?,
    private val objectDetect: Analyzer<SSDObjectDetect.Input, Unit, SSDObjectDetect.Prediction>?,
    private val bobDetect: Analyzer<BobDetect.Input, Unit, BobDetect.Prediction>?,
    private val nameAndExpiryAnalyzer: Analyzer<SSDOcr.Input, Unit, NameAndExpiryAnalyzer.Prediction>?,
) : Analyzer<SavedFrame, Unit, CompletionLoopAnalyzer.Prediction> {
    class Prediction(
        val pan: String?,
        val objectBoxes: SSDObjectDetect.Prediction?,
        val bobResult: BobDetect.Prediction?,
        val nameAndExpiryResult: NameAndExpiryAnalyzer.Prediction?,
    )

    override suspend fun analyze(
        data: SavedFrame,
        state: Unit,
    ): Prediction = supervisorScope {
        val iin = data.pan?.iin() ?: pan
        val screenDeferred = async { bobDetect?.analyze(data.frame.toScreenDetectInput(data.details), state) }
        val objectDeferred = async { objectDetect?.analyze(data.frame.toObjectDetectInput(iin, data.details), state) }
        val nameExpiryDeferred = async { nameAndExpiryAnalyzer?.analyze(data.frame, state) }

        Prediction(
            pan,
            objectDeferred.await(),
            screenDeferred.await(),
            nameExpiryDeferred.await(),
        )
    }

    class Factory(
        private val pan: String?,
        private val objectFactory: AnalyzerFactory<SSDObjectDetect.Input, Unit, SSDObjectDetect.Prediction, out Analyzer<SSDObjectDetect.Input, Unit, SSDObjectDetect.Prediction>>,
        private val bobFactory: AnalyzerFactory<BobDetect.Input, Unit, BobDetect.Prediction, out Analyzer<BobDetect.Input, Unit, BobDetect.Prediction>>,
        private val nameAndExpiryFactory: AnalyzerFactory<SSDOcr.Input, Unit, NameAndExpiryAnalyzer.Prediction, out Analyzer<SSDOcr.Input, Unit, NameAndExpiryAnalyzer.Prediction>>
    ) : AnalyzerFactory<SavedFrame, Unit, Prediction, CompletionLoopAnalyzer> {
        override suspend fun newInstance(): CompletionLoopAnalyzer? =
            CompletionLoopAnalyzer(
                pan = pan,
                objectDetect = objectFactory.newInstance(),
                bobDetect = bobFactory.newInstance(),
                nameAndExpiryAnalyzer = nameAndExpiryFactory.newInstance(),
            )
    }
}
