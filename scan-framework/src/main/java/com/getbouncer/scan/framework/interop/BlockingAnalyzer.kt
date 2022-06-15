package com.getbouncer.scan.framework.interop

import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.framework.AnalyzerPool
import com.getbouncer.scan.framework.DEFAULT_ANALYZER_PARALLEL_COUNT
import kotlinx.coroutines.runBlocking

/**
 * An implementation of an analyzer that does not use suspending functions. This allows interoperability with java.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
abstract class BlockingAnalyzer<Input, State, Output> : Analyzer<Input, State, Output> {
    override suspend fun analyze(data: Input, state: State): Output = analyzeBlocking(data, state)

    abstract fun analyzeBlocking(data: Input, state: State): Output
}

/**
 * An implementation of an analyzer factory that does not use suspending functions. This allows interoperability with
 * java.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
abstract class BlockingAnalyzerFactory<DataFrame, State, Output, AnalyzerType : Analyzer<DataFrame, State, Output>> : AnalyzerFactory<DataFrame, State, Output, AnalyzerType> {
    override suspend fun newInstance(): AnalyzerType? = newInstanceBlocking()

    abstract fun newInstanceBlocking(): AnalyzerType?
}

/**
 * An implementation of an analyzer pool factory that does not use suspending functions. This allows interoperability
 * with java.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
class BlockingAnalyzerPoolFactory<DataFrame, State, Output> @JvmOverloads constructor(
    private val analyzerFactory: AnalyzerFactory<DataFrame, State, Output, out Analyzer<DataFrame, State, Output>>,
    private val desiredAnalyzerCount: Int = DEFAULT_ANALYZER_PARALLEL_COUNT
) {
    fun buildAnalyzerPool() = AnalyzerPool(
        desiredAnalyzerCount = desiredAnalyzerCount,
        analyzers = (0 until desiredAnalyzerCount).mapNotNull {
            runBlocking { analyzerFactory.newInstance() }
        }
    )
}
