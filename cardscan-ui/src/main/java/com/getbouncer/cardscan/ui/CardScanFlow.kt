package com.getbouncer.cardscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.lifecycle.LifecycleOwner
import com.getbouncer.cardscan.ui.analyzer.CompletionLoopAnalyzer
import com.getbouncer.cardscan.ui.analyzer.MainLoopAnalyzer
import com.getbouncer.cardscan.ui.result.CompletionLoopAggregator
import com.getbouncer.cardscan.ui.result.CompletionLoopListener
import com.getbouncer.cardscan.ui.result.CompletionLoopResult
import com.getbouncer.cardscan.ui.result.MainLoopAggregator
import com.getbouncer.cardscan.ui.result.MainLoopState
import com.getbouncer.scan.camera.CameraAdapter
import com.getbouncer.scan.camera.CameraPreviewImage
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.AnalyzerPool
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.FiniteAnalyzerLoop
import com.getbouncer.scan.framework.ProcessBoundAnalyzerLoop
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.Rate
import com.getbouncer.scan.payment.FrameDetails
import com.getbouncer.scan.payment.TextDetectModelManager
import com.getbouncer.scan.payment.analyzer.NameAndExpiryAnalyzer
import com.getbouncer.scan.payment.ml.AlphabetDetect
import com.getbouncer.scan.payment.ml.AlphabetDetectModelManager
import com.getbouncer.scan.payment.ml.CardDetect
import com.getbouncer.scan.payment.ml.CardDetectModelManager
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.ExpiryDetectModelManager
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.SSDOcrModelManager
import com.getbouncer.scan.payment.ml.TextDetect
import com.getbouncer.scan.ui.ScanFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class SavedFrame(
    val pan: String?,
    val frame: MainLoopAnalyzer.Input,
    val details: FrameDetails,
)

data class SavedFrameType(
    val hasCard: Boolean,
    val hasPan: Boolean,
)

/**
 * This class contains the logic required for analyzing a credit card for scanning.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
open class CardScanFlow(
    private val enableNameExtraction: Boolean,
    private val enableExpiryExtraction: Boolean,
    private val scanResultListener: AggregateResultListener<MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult>,
    private val scanErrorListener: AnalyzerLoopErrorListener,
) : ScanFlow {
    companion object {
        private const val MAX_COMPLETION_LOOP_FRAMES_FAST_DEVICE = 8
        private const val MAX_COMPLETION_LOOP_FRAMES_SLOW_DEVICE = 5

        /**
         * Warm up the analyzers for card scanner. This method is optional, but will increase the speed at which the
         * scan occurs.
         *
         * @param context: A context to use for warming up the analyzers.
         */
        @JvmStatic
        suspend fun prepareScan(
            context: Context,
            apiKey: String,
            initializeNameAndExpiryExtraction: Boolean,
            forImmediateUse: Boolean,
        ) = withContext(Dispatchers.IO) {
            Config.apiKey = apiKey
            val deferredFetchers = mutableListOf<Deferred<FetchedData>>()

            deferredFetchers.add(async { SSDOcrModelManager.fetchModel(context, forImmediateUse) })
            deferredFetchers.add(async { CardDetectModelManager.fetchModel(context, forImmediateUse) })

            if (initializeNameAndExpiryExtraction) {
                deferredFetchers.add(async { TextDetectModelManager.fetchModel(context, forImmediateUse) })
                deferredFetchers.add(async { AlphabetDetectModelManager.fetchModel(context, forImmediateUse) })
                deferredFetchers.add(async { ExpiryDetectModelManager.fetchModel(context, forImmediateUse) })
            }

            deferredFetchers.fold(true) { acc, deferred -> acc && deferred.await().successfullyFetched }
        }

        /**
         * Determine if the scan is supported
         */
        @JvmStatic
        fun isSupported(context: Context) = CameraAdapter.isCameraSupported(context)

        /**
         * Determine if the scan models are available (have been warmed up)
         */
        @JvmStatic
        fun isScanReady() = runBlocking { SSDOcrModelManager.isReady() && CardDetectModelManager.isReady() }
    }

    /**
     * If this is true, do not start the flow.
     */
    private var canceled = false

    private var mainLoopAnalyzerPool: AnalyzerPool<MainLoopAnalyzer.Input, MainLoopState, MainLoopAnalyzer.Prediction>? = null
    private var mainLoopAggregator: MainLoopAggregator? = null
    private var mainLoop: ProcessBoundAnalyzerLoop<MainLoopAnalyzer.Input, MainLoopState, MainLoopAnalyzer.Prediction>? = null
    private var mainLoopJob: Job? = null

    private var completionLoopAnalyzerPool: AnalyzerPool<SavedFrame, Unit, CompletionLoopAnalyzer.Prediction>? = null
    private var completionLoop: FiniteAnalyzerLoop<SavedFrame, Unit, CompletionLoopAnalyzer.Prediction>? = null
    private var completionLoopJob: Job? = null

    /**
     * Start the image processing flow for scanning a card.
     *
     * @param context: The context used to download analyzers if needed
     * @param imageStream: The flow of images to process
     */
    override fun startFlow(
        context: Context,
        imageStream: Flow<CameraPreviewImage<Bitmap>>,
        viewFinder: Rect,
        lifecycleOwner: LifecycleOwner,
        coroutineScope: CoroutineScope
    ) = coroutineScope.launch(Dispatchers.Main) {
        val listener =
            object : AggregateResultListener<MainLoopAggregator.InterimResult, MainLoopAggregator.FinalResult> {
                override suspend fun onResult(result: MainLoopAggregator.FinalResult) {
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

            val analyzerPool = AnalyzerPool.of(
                MainLoopAnalyzer.Factory(
                    SSDOcr.Factory(context, SSDOcrModelManager.fetchModel(context, forImmediateUse = true, isOptional = false)),
                    CardDetect.Factory(context, CardDetectModelManager.fetchModel(context, forImmediateUse = true, isOptional = false)),
                )
            )
            mainLoopAnalyzerPool = analyzerPool

            mainLoop = ProcessBoundAnalyzerLoop(
                analyzerPool = analyzerPool,
                resultHandler = aggregator,
                analyzerLoopErrorListener = scanErrorListener,
            ).apply {
                mainLoopJob = subscribeTo(
                    imageStream.map {
                        MainLoopAnalyzer.Input(
                            cameraPreviewImage = it,
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

        completionLoop?.cancel()
        completionLoop = null

        completionLoopAnalyzerPool?.closeAllAnalyzers()
        completionLoopAnalyzerPool = null

        completionLoopJob?.apply { if (isActive) { cancel() } }
        completionLoopJob = null
    }

    open fun launchCompletionLoop(
        context: Context,
        completionResultListener: CompletionLoopListener,
        savedFrames: Collection<SavedFrame>,
        isFastDevice: Boolean,
        coroutineScope: CoroutineScope,
    ) = coroutineScope.launch {
        if (canceled) {
            return@launch
        }

        val analyzerPool = AnalyzerPool.of(
            CompletionLoopAnalyzer.Factory(
                nameAndExpiryFactory = NameAndExpiryAnalyzer.Factory(
                    textDetectFactory = TextDetect.Factory(
                        context,
                        TextDetectModelManager.fetchModel(context, forImmediateUse = true, isOptional = true)
                    ),
                    alphabetDetectFactory = AlphabetDetect.Factory(
                        context,
                        AlphabetDetectModelManager.fetchModel(context, forImmediateUse = true, isOptional = true)
                    ),
                    expiryDetectFactory = ExpiryDetect.Factory(
                        context,
                        ExpiryDetectModelManager.fetchModel(context, forImmediateUse = true, isOptional = true)
                    ),
                    runNameExtraction = enableNameExtraction && isFastDevice,
                    runExpiryExtraction = enableExpiryExtraction,
                )
            )
        )
        completionLoopAnalyzerPool = analyzerPool

        completionLoop = FiniteAnalyzerLoop(
            analyzerPool = analyzerPool,
            resultHandler = CompletionLoopAggregator(object : CompletionLoopListener {
                override fun onCompletionLoopDone(result: CompletionLoopResult) {
                    completionLoop = null

                    completionLoopAnalyzerPool?.closeAllAnalyzers()
                    completionLoopAnalyzerPool = null

                    completionLoopJob?.apply { if (isActive) { cancel() } }
                    completionLoopJob = null

                    completionResultListener.onCompletionLoopDone(result)
                }

                override fun onCompletionLoopFrameProcessed(
                    result: CompletionLoopAnalyzer.Prediction,
                    frame: SavedFrame
                ) = completionResultListener.onCompletionLoopFrameProcessed(result, frame)
            }),
            analyzerLoopErrorListener = object : AnalyzerLoopErrorListener {
                override fun onAnalyzerFailure(t: Throwable): Boolean {
                    Log.e(Config.logTag, "Completion loop analyzer failure", t)
                    completionResultListener.onCompletionLoopDone(CompletionLoopResult())
                    return true // terminate the loop on any analyzer failures
                }

                override fun onResultFailure(t: Throwable): Boolean {
                    Log.e(Config.logTag, "Completion loop result failures", t)
                    completionResultListener.onCompletionLoopDone(CompletionLoopResult())
                    return true // terminate the loop on any result failures
                }
            }
        ).apply {
            completionLoopJob = process(savedFrames, coroutineScope)
        }
    }.let { }

    /**
     * Select which frames to use in the completion loop.
     */
    open fun <SavedFrame> selectCompletionLoopFrames(
        frameRate: Rate,
        frames: Map<SavedFrameType, List<SavedFrame>>,
    ): Collection<SavedFrame> {
        fun getFrames(frameType: SavedFrameType) = frames[frameType] ?: emptyList()

        val cardAndPan = getFrames(SavedFrameType(hasCard = true, hasPan = true))
        val card = getFrames(SavedFrameType(hasCard = true, hasPan = false))
        val pan = getFrames(SavedFrameType(hasCard = false, hasPan = true))

        val maxCompletionLoopFrames =
            if (frameRate.duration <= Duration.ZERO || frameRate > Config.slowDeviceFrameRate) {
                MAX_COMPLETION_LOOP_FRAMES_FAST_DEVICE
            } else {
                MAX_COMPLETION_LOOP_FRAMES_SLOW_DEVICE
            }

        return (cardAndPan + card + pan).take(maxCompletionLoopFrames)
    }
}
