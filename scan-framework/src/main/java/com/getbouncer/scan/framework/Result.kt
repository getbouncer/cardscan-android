package com.getbouncer.scan.framework

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.ClockMark
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.framework.time.Rate
import com.getbouncer.scan.framework.time.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.LinkedList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max

/**
 * A result handler for data processing. This is called when results are available from an [Analyzer].
 */
interface ResultHandler<Input, Output, Verdict> {
    suspend fun onResult(result: Output, data: Input): Verdict
}

/**
 * A specialized result handler that has some form of state.
 */
abstract class StatefulResultHandler<Input, State, Output, Verdict>(
    private var initialState: State
) : ResultHandler<Input, Output, Verdict> {

    /**
     * The state of the result handler. This can be read, but not updated by analyzers.
     */
    var state: State = initialState
        protected set

    /**
     * Reset the state to the initial value.
     */
    protected open fun reset() { state = initialState }
}

/**
 * A frame and its result that is saved for later analysis.
 */
data class SavedFrame<DataFrame, State, Result>(val data: DataFrame, val state: State, val result: Result)

interface AggregateResultListener<DataFrame, State, InterimResult, FinalResult> {

    /**
     * The aggregated result of an [AnalyzerLoop] is available.
     *
     * @param result: the result from the [AnalyzerLoop]
     * @param frames: data frames captured during processing that can be used in the completion loop
     */
    suspend fun onResult(result: FinalResult, frames: Map<String, List<SavedFrame<DataFrame, State, InterimResult>>>)

    /**
     * An interim result is available, but the [AnalyzerLoop] is still processing more data frames. This is useful for
     * displaying a debug window or handling state updates during a scan.
     *
     * @param result: the result from the [AnalyzerLoop]
     * @param state: the shared [State] that produced this result
     * @param frame: the data frame that produced this result
     */
    suspend fun onInterimResult(result: InterimResult, state: State, frame: DataFrame)

    /**
     * The result aggregator was reset back to its original state.
     */
    suspend fun onReset()
}

/**
 * A result handler with a method that notifies when all data has been processed.
 */
abstract class TerminatingResultHandler<Input, State, Output>(
    initialState: State
) : StatefulResultHandler<Input, State, Output, Unit>(initialState) {
    /**
     * All data has been processed and termination was reached.
     */
    abstract suspend fun onAllDataProcessed()

    /**
     * Not all data was processed before termination.
     */
    abstract suspend fun onTerminatedEarly()
}

/**
 * A simple class by which results can be stored.
 */
class ResultCounter<T> {
    private val resultMutex = Mutex()
    private val results = mutableMapOf<T, Int>()

    /**
     * Get the result that was most frequently seen.
     *
     * @param minCount the minimum times a result must have been seen.
     */
    fun getMostLikelyResult(minCount: Int = 1): T? {
        val candidate = results.maxBy { it.value }?.key
        return if (results[candidate] ?: 0 >= minCount) candidate else null
    }

    /**
     * Store the value of a result. Return the number of matching results previously seen.
     */
    suspend fun countResult(field: T): Int = resultMutex.withLock {
        val count = 1 + (results[field] ?: 0)
        results[field] = count
        count
    }

    suspend fun reset() = resultMutex.withLock {
        results.clear()
    }
}

/**
 * Configuration for a result aggregator
 */
data class ResultAggregatorConfig internal constructor(
    val maxTotalAggregationTime: Duration,
    val maxSavedFrames: Map<String, Int>,
    val defaultMaxSavedFrames: Int?,
    val frameStorageBytes: Map<String, Int>,
    val defaultFrameStorageBytes: Int?,
    val trackFrameRate: Boolean,
    val frameRateUpdateInterval: Duration
) {

    class Builder {
        companion object {
            private val DEFAULT_MAX_TOTAL_AGGREGATION_TIME = 1.5.seconds
            private val DEFAULT_MAX_SAVED_FRAMES = null // Frame storage is not limited by count
            private const val DEFAULT_FRAME_STORAGE_BYTES = 0x2000000 // 32MB
            private val DEFAULT_TRACK_FRAME_RATE = Config.isDebug
            private val DEFAULT_FRAME_RATE_UPDATE_INTERVAL = 1.seconds
        }

        private var maxTotalAggregationTime: Duration = DEFAULT_MAX_TOTAL_AGGREGATION_TIME

        private var maxSavedFrames: MutableMap<String, Int> = mutableMapOf()
        private var defaultMaxSavedFrames: Int? = DEFAULT_MAX_SAVED_FRAMES

        private var frameStorageBytes: MutableMap<String, Int> = mutableMapOf()
        private var defaultFrameStorageBytes: Int? = DEFAULT_FRAME_STORAGE_BYTES

        private var trackFrameRate: Boolean = DEFAULT_TRACK_FRAME_RATE
        private var frameRateUpdateInterval: Duration = DEFAULT_FRAME_RATE_UPDATE_INTERVAL

        fun withMaxTotalAggregationTime(maxTotalAggregationTime: Duration) = this.apply {
            this.maxTotalAggregationTime = maxTotalAggregationTime
        }

        fun withMaxSavedFrames(maxSavedFrames: Map<String, Int>) = this.apply {
            this.maxSavedFrames = maxSavedFrames.toMutableMap()
        }

        fun withMaxSavedFrames(frameType: String, maxSavedFrames: Int) = this.apply {
            this.maxSavedFrames[frameType] = maxSavedFrames
        }

        fun withDefaultMaxSavedFrames(defaultMaxSavedFrames: Int) = this.apply {
            this.defaultMaxSavedFrames = defaultMaxSavedFrames
        }

        fun withFrameRateUpdateInterval(frameRateUpdateInterval: Duration) = this.apply {
            this.frameRateUpdateInterval = frameRateUpdateInterval
        }

        fun withFrameStorageBytes(frameStorageBytes: Map<String, Int>) = this.apply {
            this.frameStorageBytes = frameStorageBytes.toMutableMap()
        }

        fun withFrameStorageBytes(frameType: String, frameStorageBytes: Int) = this.apply {
            this.frameStorageBytes[frameType] = frameStorageBytes
        }

        fun withDefaultFrameStorageBytes(defaultFrameStorageBytes: Int) = this.apply {
            this.defaultFrameStorageBytes = defaultFrameStorageBytes
        }

        fun withTrackFrameRate(trackFrameRate: Boolean) = this.apply {
            this.trackFrameRate = trackFrameRate
        }

        fun build() =
            ResultAggregatorConfig(
                maxTotalAggregationTime,
                maxSavedFrames,
                defaultMaxSavedFrames,
                frameStorageBytes,
                defaultFrameStorageBytes,
                trackFrameRate,
                frameRateUpdateInterval
            )
    }
}

/**
 * The result aggregator processes results from an analyzer until a condition specified in the configuration is met,
 * either total aggregation time elapses or required agreement count is met.
 */
abstract class ResultAggregator<DataFrame, State, AnalyzerResult, InterimResult, FinalResult>(
    private val config: ResultAggregatorConfig,
    private val listener: AggregateResultListener<DataFrame, State, InterimResult, FinalResult>,
    initialState: State
) : StatefulResultHandler<DataFrame, State, AnalyzerResult, Boolean>(initialState), LifecycleObserver {
    private var firstResultTime: ClockMark? = null
    private var firstFrameTime: ClockMark? = null
    private var lastNotifyTime: ClockMark = Clock.markNow()
    private val totalFramesProcessed: AtomicLong = AtomicLong(0)
    private val framesProcessedSinceLastUpdate: AtomicLong = AtomicLong(0)
    private val haveSeenValidResult = AtomicBoolean(false)

    private var isCanceled = false
    private var isPaused = false
    private var isFinished = false

    private val savedFrames = mutableMapOf<String, LinkedList<SavedFrame<DataFrame, State, InterimResult>>>()
    private val savedFramesSizeBytes = mutableMapOf<String, Int>()

    private val aggregatorExecutionStats = runBlocking { Stats.trackRepeatingTask("${name}_aggregator_execution") }
    private val firstValidFrameStats = runBlocking { Stats.trackTask("${name}_aggregator_first_valid_frame") }

    private val saveFrameMutex = Mutex()
    private val frameRateMutex = Mutex()

    protected abstract val name: String

    /**
     * Reset the state of the aggregator and pause aggregation. This is useful for aggregators that can be backgrounded.
     * For example, a user that is scanning an object, but then backgrounds the scanning app. In the case that the scan
     * should be restarted, this feature pauses the result handlers and resets the state.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private fun resetAndPause() {
        isPaused = true
        reset()
    }

    /**
     * Resume aggregation after it has been paused.
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private fun resume() {
        isPaused = false
    }

    /**
     * Cancel a result aggregator. This means that the result aggregator will ignore all further results and will never
     * return a final result.
     */
    fun cancel() {
        isCanceled = true
        reset()
    }

    /**
     * Bind this result aggregator to a lifecycle. This allows the result aggregator to pause and reset when the
     * lifecycle owner pauses.
     */
    open fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
    }

    /**
     * Reset the state of the aggregator. This is useful for aggregating data that can become invalid, such as when a
     * user is scanning an object, and moves the object away from the camera before the scan has completed.
     */
    override fun reset() {
        super.reset()
        firstResultTime = null
        firstFrameTime = null
        haveSeenValidResult.set(false)
        totalFramesProcessed.set(0)
        framesProcessedSinceLastUpdate.set(0)

        runBlocking {
            saveFrameMutex.withLock {
                savedFrames.clear()
                savedFramesSizeBytes.clear()
            }
        }

        runBlocking { listener.onReset() }
    }

    override suspend fun onResult(result: AnalyzerResult, data: DataFrame): Boolean = when {
        isPaused -> false
        isCanceled || isFinished -> true
        else -> {
            withContext(Dispatchers.Default) {
                if (config.trackFrameRate) {
                    trackAndNotifyOfFrameRate()
                }

                val resultPair = aggregateResult(
                    result = result,
                    startAggregationTimer = {
                        if (firstResultTime == null) {
                            firstResultTime = Clock.markNow()
                        }
                    },
                    mustReturnFinal = hasReachedTimeout()
                )
                val interimResult = resultPair.first
                val finalResult = resultPair.second

                saveFrames(interimResult, data)

                launch {
                    listener.onInterimResult(
                        result = interimResult,
                        state = state,
                        frame = data
                    )
                }

                aggregatorExecutionStats.trackResult("frame_processed")
                if (finalResult != null) {
                    isFinished = true
                    launch { listener.onResult(finalResult, savedFrames) }
                }

                isFinished
            }
        }
    }

    /**
     * Determine how frames should be classified using [getSaveFrameIdentifier], and then store them in a map of frames
     * based on that identifier.
     *
     * This method keeps track of the total number of saved frames and the total size of saved frames. If the total
     * number or total size exceeds the maximum allowed in the aggregator configuration, the oldest frames will be
     * dropped.
     */
    private suspend fun saveFrames(result: InterimResult, data: DataFrame) {
        val savedFrameType = getSaveFrameIdentifier(result, data) ?: return
        return saveFrameMutex.withLock {
            val typedSavedFrames = savedFrames[savedFrameType] ?: LinkedList()

            val maxSavedFrames = config.maxSavedFrames[savedFrameType] ?: config.defaultMaxSavedFrames
            val storageBytes = config.frameStorageBytes[savedFrameType] ?: config.defaultFrameStorageBytes

            var typedSizeBytes = (savedFramesSizeBytes[savedFrameType] ?: 0) + getFrameSizeBytes(data)
            while (storageBytes != null && typedSizeBytes > storageBytes) {
                // saved frames is over storage limit, reduce until it's not
                if (typedSavedFrames.size > 0) {
                    val removedFrame = typedSavedFrames.removeFirst()
                    typedSizeBytes -= getFrameSizeBytes(removedFrame.data)
                } else {
                    typedSizeBytes = 0
                }
            }

            while (maxSavedFrames != null && typedSavedFrames.size > maxSavedFrames) {
                // saved frames is over size limit, reduce until it's not
                val removedFrame = typedSavedFrames.removeFirst()
                typedSizeBytes = max(0, typedSizeBytes - getFrameSizeBytes(removedFrame.data))
            }

            savedFramesSizeBytes[savedFrameType] = typedSizeBytes
            typedSavedFrames.add(SavedFrame(data, state, result))
            savedFrames[savedFrameType] = typedSavedFrames
        }
    }

    /**
     * Aggregate a new result. Note that the [result] may be invalid. If this method returns a non-null
     * [AnalyzerResult], the aggregator will stop listening for new results.
     *
     * @param result: The result to aggregate
     * @param startAggregationTimer: When called, the maximum aggregation timer starts.
     * @param mustReturnFinal: If true, this method must return a final result
     */
    abstract suspend fun aggregateResult(
        result: AnalyzerResult,
        startAggregationTimer: () -> Unit,
        mustReturnFinal: Boolean
    ): Pair<InterimResult, FinalResult?>

    /**
     * Determine if a data frame should be saved for future processing. Note that [result] may be invalid.
     *
     * If this method returns a non-null string, the frame will be saved under that identifier.
     */
    abstract fun getSaveFrameIdentifier(result: InterimResult, frame: DataFrame): String?

    /**
     * Determine the size in memory that this data frame takes up.
     */
    abstract fun getFrameSizeBytes(frame: DataFrame): Int

    /**
     * Calculate the current rate at which the [AnalyzerLoop] is processing images. Notify the
     * listener of the result.
     */
    private suspend fun trackAndNotifyOfFrameRate() {
        val totalFrames = totalFramesProcessed.incrementAndGet()
        val framesSinceLastUpdate = framesProcessedSinceLastUpdate.incrementAndGet()

        val lastNotifyTime = this.lastNotifyTime
        val shouldNotifyOfFrameRate = frameRateMutex.withLock {
            val shouldNotify = shouldNotifyOfFrameRate(this.lastNotifyTime)
            if (shouldNotify) {
                this.lastNotifyTime = Clock.markNow()
            }
            shouldNotify
        }

        val firstFrameTime = this.firstFrameTime ?: Clock.markNow()
        this.firstFrameTime = firstFrameTime

        if (shouldNotifyOfFrameRate) {
            val totalFrameRate = Rate(totalFrames, firstFrameTime.elapsedSince())
            val instantFrameRate = Rate(framesSinceLastUpdate, lastNotifyTime.elapsedSince())

            logProcessingRate(totalFrameRate, instantFrameRate)
            framesProcessedSinceLastUpdate.set(0)
        }
    }

    /**
     * Allow aggregators to get an immutable list of frames.
     */
    protected fun getSavedFrames():
        Map<String, LinkedList<SavedFrame<DataFrame, State, InterimResult>>> = savedFrames

    /**
     * Determine if enough time has elapsed since the last frame rate update.
     */
    private fun shouldNotifyOfFrameRate(lastNotifyTime: ClockMark) =
        lastNotifyTime.elapsedSince() > config.frameRateUpdateInterval

    /**
     * The processing rate has been updated. This is useful for debugging and measuring performance.
     *
     * @param overallRate: The total frame rate at which the analyzer is running
     * @param instantRate: The instantaneous frame rate at which the analyzer is running
     */
    private fun logProcessingRate(overallRate: Rate, instantRate: Rate) {
        val overallFps = if (overallRate.duration != Duration.ZERO) {
            overallRate.amount / overallRate.duration.inSeconds
        } else {
            0.0
        }

        val instantFps = if (instantRate.duration != Duration.ZERO) {
            instantRate.amount / instantRate.duration.inSeconds
        } else {
            0.0
        }

        if (Config.isDebug) {
            Log.d(Config.logTag, "Aggregator $name processing avg=$overallFps, inst=$instantFps")
        }
    }

    /**
     * Determine if the timeout from the config has been reached
     */
    private fun hasReachedTimeout(): Boolean =
        firstResultTime?.elapsedSince() ?: Duration.NEGATIVE_INFINITE > config.maxTotalAggregationTime
}
