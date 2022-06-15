package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.ResultAggregator
import com.getbouncer.scan.framework.ResultHandler
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
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
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
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
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
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
abstract class BlockingAggregateResultListener<InterimResult, FinalResult> :
    AggregateResultListener<InterimResult, FinalResult> {
    override suspend fun onInterimResult(result: InterimResult) = onInterimResultBlocking(result)

    override suspend fun onResult(result: FinalResult) = onResultBlocking(result)

    override suspend fun onReset() = onResetBlocking()

    abstract fun onInterimResultBlocking(result: InterimResult)

    abstract fun onResultBlocking(result: FinalResult)

    abstract fun onResetBlocking()
}

/**
 * An implementation of a result aggregator that does not use suspending functions. This allows interoperability with
 * java.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
abstract class BlockingResultAggregator<DataFrame, State, AnalyzerResult, InterimResult, FinalResult>(
    listener: AggregateResultListener<InterimResult, FinalResult>,
    initialState: State
) : ResultAggregator<DataFrame, State, AnalyzerResult, InterimResult, FinalResult>(listener, initialState) {
    override suspend fun aggregateResult(frame: DataFrame, result: AnalyzerResult): Pair<InterimResult, FinalResult?> =
        aggregateResultBlocking(frame, result)

    abstract fun aggregateResultBlocking(frame: DataFrame, result: AnalyzerResult): Pair<InterimResult, FinalResult?>
}
