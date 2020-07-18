package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.framework.ResultAggregatorConfig
import com.getbouncer.scan.framework.ResultHandler
import com.getbouncer.scan.framework.SavedFrame
import com.getbouncer.scan.framework.StatefulResultHandler
import com.getbouncer.scan.framework.TerminatingResultHandler

/**
 * An implementation of a result handler that does not use suspending functions. This allows interoperability with java.
 */
abstract class BlockingResultHandler<Input, Output, Verdict> : ResultHandler<Input, Output, Verdict> {
    override suspend fun onResult(result: Output, data: Input) = onResultBlocking(result, data)

    abstract fun onResultBlocking(result: Output, data: Input): Verdict
}

/**
 * An implementation of a stateful result handler that does not use suspending functions. This allows interoperability
 * with java.
 */
abstract class BlockingStatefulResultHandler<Input, State, Output, Verdict>(
    initialState: State
) : StatefulResultHandler<Input, State, Output, Verdict>(initialState) {
    override suspend fun onResult(result: Output, data: Input): Verdict = onResultBlocking(result, data)

    abstract fun onResultBlocking(result: Output, data: Input): Verdict
}

/**
 * An implementation of a terminating result handler that does not use suspending functions. This allows
 * interoperability with java.
 */
abstract class BlockingTerminatingResultHandler<Input, State, Output>(
    initialState: State
) : TerminatingResultHandler<Input, State, Output>(initialState) {
    override suspend fun onResult(result: Output, data: Input) = onResultBlocking(result, data)

    override suspend fun onTerminatedEarly() = onTerminatedEarlyBlocking()

    override suspend fun onAllDataProcessed() = onAllDataProcessedBlocking()

    abstract fun onResultBlocking(result: Output, data: Input)

    abstract fun onTerminatedEarlyBlocking()

    abstract fun onAllDataProcessedBlocking()
}

/**
 * An implementation of a result listener that does not use suspending functions. This allows interoperability with
 * java.
 */
abstract class BlockingAggregateResultListener<DataFrame, State, InterimResult, FinalResult> :
    AggregateResultListener<DataFrame, State, InterimResult, FinalResult> {
    override suspend fun onInterimResult(result: InterimResult, state: State, frame: DataFrame) =
        onInterimResultBlocking(result, state, frame)

    override suspend fun onResult(
        result: FinalResult,
        frames: Map<String, List<SavedFrame<DataFrame, State, InterimResult>>>
    ) = onResultBlocking(result, frames)

    override suspend fun onReset() = onResetBlocking()

    abstract fun onInterimResultBlocking(result: InterimResult, state: State, frame: DataFrame)

    abstract fun onResultBlocking(
        result: FinalResult,
        frames: Map<String, List<SavedFrame<DataFrame, State, InterimResult>>>
    )

    abstract fun onResetBlocking()
}

/**
 * An implementation of a result aggregator that does not use suspending functions. This allows interoperability with
 * java.
 */
abstract class BlockingResultAggregator<DataFrame, State, AnalyzerResult, InterimResult, FinalResult>(
    config: ResultAggregatorConfig,
    listener: AggregateResultListener<DataFrame, State, InterimResult, FinalResult>,
    initialState: State
) : ResultAggregator<DataFrame, State, AnalyzerResult, InterimResult, FinalResult>(config, listener, initialState) {
    override suspend fun aggregateResult(
        result: AnalyzerResult,
        startAggregationTimer: () -> Unit,
        mustReturnFinal: Boolean
    ): Pair<InterimResult, FinalResult?> = aggregateResultBlocking(result, startAggregationTimer, mustReturnFinal)

    abstract fun aggregateResultBlocking(
        result: AnalyzerResult,
        startAggregationTimer: () -> Unit,
        mustReturnFinal: Boolean
    ): Pair<InterimResult, FinalResult?>
}
