package com.getbouncer.cardscan.ui.local

import android.content.Context
import android.graphics.Rect
import android.util.Size
import androidx.lifecycle.LifecycleOwner
import com.getbouncer.cardscan.ui.local.result.MainLoopAggregator
import com.getbouncer.cardscan.ui.local.result.MainLoopState
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.AnalyzerPool
import com.getbouncer.scan.framework.ProcessBoundAnalyzerLoop
import com.getbouncer.scan.framework.ResourceFetcher
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.ui.ScanFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * This class contains the logic required for analyzing a credit card for scanning.
 */
open class CardScanFlow(
    private val scanResultListener: AggregateResultListener<MainLoopAggregator.InterimResult, String>,
    private val scanErrorListener: AnalyzerLoopErrorListener,
) : ScanFlow {
    companion object {
        private suspend fun getSsdOcr() = object : ResourceFetcher() {
//            override val modelVersion: String = "1.1.1.16"
//            override val hash: String = "8d8e3f79aa0783ab0cfa5c8d65d663a9da6ba99401efb2298aaaee387c3b00d6"
//            override val resource: Int = R.raw.darknite_1_1_1_16
            override val assetFileName: String = "mb2_brex_metal_synthetic_svhnextra_epoch_3_5_98_8.tflite"
            override val modelVersion: String = "3.5.98.8"
            override val hash: String = "a4739fa49caa3ff88e7ff1145c9334ee4cbf64354e91131d02d98d7bfd4c35cf"
            override val hashAlgorithm: String = "SHA-256"
            override val modelClass: String = "ocr"
            override val modelFrameworkVersion: Int = 1
        }.fetchData(forImmediateUse = true, isOptional = false)
    }

    /**
     * If this is true, do not start the flow.
     */
    private var canceled = false

    private var mainLoopAnalyzerPool: AnalyzerPool<SSDOcr.Input, Any, SSDOcr.Prediction>? = null
    private var mainLoopAggregator: MainLoopAggregator? = null
    private var mainLoop: ProcessBoundAnalyzerLoop<SSDOcr.Input, MainLoopState, SSDOcr.Prediction>? = null
    private var mainLoopJob: Job? = null

    /**
     * Start the image processing flow for scanning a card.
     *
     * @param context: The context used to download analyzers if needed
     * @param imageStream: The flow of images to process
     * @param previewSize: The size of the preview frame where the view finder is located
     */
    override fun startFlow(
        context: Context,
        imageStream: Flow<TrackedImage>,
        previewSize: Size,
        viewFinder: Rect,
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope
    ) = coroutineScope.launch {
        val listener =
            object : AggregateResultListener<MainLoopAggregator.InterimResult, String> {
                override suspend fun onResult(result: String) {
                    mainLoop?.unsubscribe()
                    mainLoop = null

                    mainLoopJob?.apply { if (isActive) { cancel() } }
                    mainLoopJob = null

                    mainLoopAggregator = null

                    mainLoopAnalyzerPool?.closeAllAnalyzers()
                    mainLoopAnalyzerPool = null

                    scanResultListener.onResult(result)
                }

                override suspend fun onInterimResult(result: MainLoopAggregator.InterimResult) {
                    scanResultListener.onInterimResult(result)
                }

                override suspend fun onReset() {
                    scanResultListener.onReset()
                }
            }

        if (canceled) {
            return@launch
        }

        mainLoopAggregator = MainLoopAggregator(listener).also { aggregator ->
            // make this result aggregator pause and reset when the lifecycle pauses.
            aggregator.bindToLifecycle(lifecycleOwner)

            val analyzerPool = AnalyzerPool.of(SSDOcr.Factory(context, getSsdOcr()))
            mainLoopAnalyzerPool = analyzerPool

            mainLoop = ProcessBoundAnalyzerLoop(
                analyzerPool = analyzerPool,
                resultHandler = aggregator,
                analyzerLoopErrorListener = scanErrorListener,
            ).apply {
                mainLoopJob = subscribeTo(
                    imageStream.map {
                        SSDOcr.Input(
                            fullImage = it,
                            previewSize = previewSize,
                            cardFinder = viewFinder,
                        )
                    },
                    coroutineScope,
                )
            }
        }
    }.let { }

    /**
     * In the event that the scan cannot complete, halt the flow to halt analyzers and free up CPU and memory.
     */
    override fun cancelFlow() {
        canceled = true

        mainLoopAggregator?.run { cancel() }
        mainLoopAggregator = null

        mainLoop?.unsubscribe()
        mainLoop = null

        mainLoopAnalyzerPool?.closeAllAnalyzers()
        mainLoopAnalyzerPool = null

        mainLoopJob?.apply { if (isActive) { cancel() } }
        mainLoopJob = null
    }
}
