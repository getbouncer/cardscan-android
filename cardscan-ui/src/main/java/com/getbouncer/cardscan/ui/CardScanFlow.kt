package com.getbouncer.cardscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Size
import androidx.lifecycle.LifecycleOwner
import com.getbouncer.cardscan.ui.analyzer.MainLoopNameExpiryAnalyzer
import com.getbouncer.cardscan.ui.analyzer.MainLoopOcrAnalyzer
import com.getbouncer.cardscan.ui.result.MainLoopNameExpiryAggregator
import com.getbouncer.cardscan.ui.result.MainLoopNameExpiryState
import com.getbouncer.cardscan.ui.result.MainLoopOcrAggregator
import com.getbouncer.cardscan.ui.result.MainLoopOcrState
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.AnalyzerPool
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.ProcessBoundAnalyzerLoop
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.Rate
import com.getbouncer.scan.framework.util.cacheFirstResultSuspend
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.ml.AlphabetDetect
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.TextDetect
import com.getbouncer.scan.ui.ScanFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * This class contains the logic required for analyzing a credit card for scanning.
 */
class CardScanFlow(
    private val enableNameExtraction: Boolean,
    private val enableExpiryExtraction: Boolean,
    private val resultListener: AggregateResultListener<InterimResult, FinalResult>,
    private val errorListener: AnalyzerLoopErrorListener
) : ScanFlow {
    companion object {

        /**
         * This field represents whether the flow was initialized with name and expiry enabled.
         */
        var attemptedNameAndExpiryInitialization = false
            private set

        private val getSsdOcrModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
            SSDOcr.ModelFetcher(context).fetchData(forImmediateUse)
        }
        private val getTextDetectorModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
            TextDetect.ModelFetcher(context).fetchData(forImmediateUse)
        }
        private val getAlphabetDetectorModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
            AlphabetDetect.ModelFetcher(context).fetchData(forImmediateUse)
        }
        private val getExpiryDetectorModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
            ExpiryDetect.ModelFetcher(context).fetchData(forImmediateUse)
        }

        /**
         * Warm up the analyzers for card scanner. This method is optional, but will increase the speed at which the
         * scan occurs.
         *
         * @param context: A context to use for warming up the analyzers.
         */
        @JvmStatic
        fun warmUp(context: Context, apiKey: String, initializeNameAndExpiryExtraction: Boolean) {
            Config.apiKey = apiKey

            // pre-fetch all of the models used by this flow.
            GlobalScope.launch(Dispatchers.IO) { getSsdOcrModel(context, false) }

            if (initializeNameAndExpiryExtraction) {
                attemptedNameAndExpiryInitialization = true
                GlobalScope.launch(Dispatchers.IO) { getTextDetectorModel(context, false) }
                GlobalScope.launch(Dispatchers.IO) { getAlphabetDetectorModel(context, false) }
                GlobalScope.launch(Dispatchers.IO) { getExpiryDetectorModel(context, false) }
            }
        }
    }

    data class InterimResult(
        val ocrAnalyzerResult: SSDOcr.Prediction?,
        val ocrState: MainLoopOcrState?,
        val nameExpiryAnalyzerResult: MainLoopNameExpiryAnalyzer.Prediction?,
        val nameExpiryState: MainLoopNameExpiryState?,
        val frame: SSDOcr.Input,
    )

    data class FinalResult(
        val pan: String?,
        val name: String?,
        val expiry: ExpiryDetect.Expiry?,
        val errorString: String?,
    )

    /**
     * If this is true, do not start the flow.
     */
    private var canceled = false

    private var mainLoopOcrAggregator: MainLoopOcrAggregator? = null
    private var mainLoopNameExpiryAggregator: MainLoopNameExpiryAggregator? = null

    private var mainLoopOcr: ProcessBoundAnalyzerLoop<SSDOcr.Input, MainLoopOcrState, SSDOcr.Prediction>? = null
    private var mainLoopNameExpiry: ProcessBoundAnalyzerLoop<SSDOcr.Input, MainLoopNameExpiryState, MainLoopNameExpiryAnalyzer.Prediction>? = null

    private var mainLoopOcrJob: Job? = null
    private var mainLoopNameExpiryJob: Job? = null

    private var pan: String? = null

    /**
     * Start the image processing flow for scanning a card.
     *
     * @param context: The context used to download analyzers if needed
     * @param imageStream: The flow of images to process
     * @param previewSize: The size of the preview frame where the view finder is located
     */
    override fun startFlow(
        context: Context,
        imageStream: Flow<Bitmap>,
        previewSize: Size,
        viewFinder: Rect,
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope
    ) = coroutineScope.launch {

        pan = null

        val mainLoopNameExpiryListener =
            object : AggregateResultListener<MainLoopNameExpiryAggregator.InterimResult, MainLoopNameExpiryAggregator.FinalResult> {
                override suspend fun onResult(result: MainLoopNameExpiryAggregator.FinalResult) {
                    mainLoopNameExpiry?.unsubscribe()
                    mainLoopNameExpiry = null

                    mainLoopNameExpiryJob?.cancel()
                    mainLoopNameExpiryAggregator = null

                    resultListener.onResult(
                        FinalResult(
                            pan = pan,
                            name = result.name,
                            expiry = result.expiry,
                            errorString = result.errorString,
                        )
                    )
                }

                override suspend fun onInterimResult(result: MainLoopNameExpiryAggregator.InterimResult) {
                    resultListener.onInterimResult(
                        InterimResult(
                            ocrAnalyzerResult = null,
                            ocrState = null,
                            nameExpiryAnalyzerResult = result.analyzerResult,
                            nameExpiryState = result.state,
                            frame = result.frame,
                        )
                    )
                }

                override suspend fun onReset() {
                    resultListener.onReset()
                }
            }

        val mainLoopOcrListener =
            object : AggregateResultListener<MainLoopOcrAggregator.InterimResult, MainLoopOcrAggregator.FinalResult> {
                override suspend fun onResult(result: MainLoopOcrAggregator.FinalResult) {
                    mainLoopOcr?.unsubscribe()
                    mainLoopOcr = null

                    pan = result.pan

                    val isDeviceFastEnough = isDeviceFastEnoughForNameExtraction(result.averageFrameRate)

                    mainLoopOcrJob?.cancel()
                    mainLoopOcrAggregator = null

                    if (enableNameExtraction || enableExpiryExtraction) {
                        coroutineScope.launch {
                            runNameExpiryMainLoop(
                                context,
                                imageStream,
                                previewSize,
                                viewFinder,
                                lifecycleOwner,
                                coroutineScope,
                                isDeviceFastEnough,
                                mainLoopNameExpiryListener,
                            )
                        }
                    } else {
                        resultListener.onResult(
                            FinalResult(
                                pan = result.pan,
                                name = null,
                                expiry = null,
                                errorString = null,
                            )
                        )
                    }
                }

                override suspend fun onInterimResult(result: MainLoopOcrAggregator.InterimResult) {
                    resultListener.onInterimResult(
                        InterimResult(
                            ocrAnalyzerResult = result.analyzerResult,
                            ocrState = result.state,
                            nameExpiryAnalyzerResult = null,
                            nameExpiryState = null,
                            frame = result.frame,
                        )
                    )
                }

                override suspend fun onReset() {
                    resultListener.onReset()
                }
            }

        runOcrMainLoop(
            context,
            imageStream,
            previewSize,
            viewFinder,
            lifecycleOwner,
            coroutineScope,
            mainLoopOcrListener,
        )
    }.let { Unit }

    /**
     * In the event that the scan cannot complete, halt the flow to halt analyzers and free up CPU and memory.
     */
    override fun cancelFlow() {
        canceled = true

        mainLoopNameExpiryAggregator?.run { cancel() }
        mainLoopNameExpiryAggregator = null

        mainLoopOcrAggregator?.run { cancel() }
        mainLoopOcrAggregator = null

        mainLoopNameExpiry?.unsubscribe()
        mainLoopNameExpiry = null

        mainLoopOcr?.unsubscribe()
        mainLoopOcr = null

        mainLoopNameExpiryJob?.apply { if (isActive) { cancel() } }
        mainLoopNameExpiryJob = null

        mainLoopOcrJob?.apply { if (isActive) { cancel() } }
        mainLoopOcrJob = null
    }

    private fun isDeviceFastEnoughForNameExtraction(processingRate: Rate?) =
        processingRate != null &&
            (processingRate.duration <= Duration.ZERO || processingRate > Config.slowDeviceFrameRate)

    private fun runOcrMainLoop(
        context: Context,
        imageStream: Flow<Bitmap>,
        previewSize: Size,
        viewFinder: Rect,
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope,
        listener: AggregateResultListener<MainLoopOcrAggregator.InterimResult, MainLoopOcrAggregator.FinalResult>,
    ) {
        if (canceled) {
            return
        }

        mainLoopOcrAggregator = MainLoopOcrAggregator(
            listener = listener,
            enableNameExpiryExtraction = enableNameExtraction || enableExpiryExtraction,
        ).also { mainLoopOcrAggregator ->
            // make this result aggregator pause and reset when the lifecycle pauses.
            mainLoopOcrAggregator.bindToLifecycle(lifecycleOwner)

            val analyzerPool = runBlocking {
                AnalyzerPool.of(
                    MainLoopOcrAnalyzer.Factory(
                        SSDOcr.Factory(context, getSsdOcrModel(context, true))
                    )
                )
            }

            mainLoopOcr = ProcessBoundAnalyzerLoop(
                analyzerPool = analyzerPool,
                resultHandler = mainLoopOcrAggregator,
                analyzerLoopErrorListener = errorListener,
            ).apply {
                subscribeTo(
                    imageStream.map {
                        SSDOcr.Input(
                            fullImage = it,
                            previewSize = previewSize,
                            cardFinder = viewFinder,
                            capturedAt = Clock.markNow(),
                        )
                    },
                    coroutineScope,
                )
            }
        }
    }

    private fun runNameExpiryMainLoop(
        context: Context,
        imageStream: Flow<Bitmap>,
        previewSize: Size,
        viewFinder: Rect,
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope,
        isDeviceFastEnough: Boolean,
        listener: AggregateResultListener<MainLoopNameExpiryAggregator.InterimResult, MainLoopNameExpiryAggregator.FinalResult>,
    ) {
        if (canceled) {
            return
        }

        mainLoopNameExpiryAggregator = MainLoopNameExpiryAggregator(
            listener = listener,
            enableNameExtraction = enableNameExtraction && isDeviceFastEnough,
            enableExpiryExtraction = enableExpiryExtraction,
        ).also { mainLoopNameExpiryAggregator ->

            // make this result aggregator pause and reset when the lifecycle pauses.
            mainLoopNameExpiryAggregator.bindToLifecycle(lifecycleOwner)

            val analyzerPool = runBlocking {
                AnalyzerPool.of(
                    MainLoopNameExpiryAnalyzer.Factory(
                        NameAndExpiryAnalyzer.Factory(
                            TextDetect.Factory(context, getTextDetectorModel(context, true)),
                            AlphabetDetect.Factory(context, getAlphabetDetectorModel(context, true)),
                            ExpiryDetect.Factory(context, getExpiryDetectorModel(context, true)),
                            runNameExtraction = enableNameExtraction && isDeviceFastEnough,
                            runExpiryExtraction = enableExpiryExtraction,
                        )
                    )
                )
            }

            mainLoopNameExpiry = ProcessBoundAnalyzerLoop(
                analyzerPool = analyzerPool,
                resultHandler = mainLoopNameExpiryAggregator,
                analyzerLoopErrorListener = errorListener,
            ).apply {
                subscribeTo(
                    imageStream.map {
                        SSDOcr.Input(
                            fullImage = it,
                            previewSize = previewSize,
                            cardFinder = viewFinder,
                            capturedAt = Clock.markNow(),
                        )
                    },
                    coroutineScope,
                )
            }
        }
    }
}
