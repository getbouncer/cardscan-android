package com.getbouncer.scan.framework

/**
 * The default number of analyzers to run in parallel.
 */
internal const val DEFAULT_ANALYZER_PARALLEL_COUNT = 3

/**
 * An analyzer takes some data as an input, and returns an analyzed output. Analyzers should not
 * contain any state. They must define whether they can run on a multithreaded executor, and provide
 * a means of analyzing input data to return some form of result.
 */
interface Analyzer<Input, State, Output> {
    suspend fun analyze(data: Input, state: State): Output
}

/**
 * A factory to create analyzers.
 */
interface AnalyzerFactory<Input, State, Output, AnalyzerType : Analyzer<Input, State, Output>> {
    suspend fun newInstance(): AnalyzerType?
}

/**
 * A pool of analyzers.
 */
data class AnalyzerPool<DataFrame, State, Output>(
    val desiredAnalyzerCount: Int,
    val analyzers: List<Analyzer<DataFrame, State, Output>>
) {
    companion object {
        suspend fun <DataFrame, State, Output> of(
            analyzerFactory: AnalyzerFactory<DataFrame, State, Output, out Analyzer<DataFrame, State, Output>>,
            desiredAnalyzerCount: Int = DEFAULT_ANALYZER_PARALLEL_COUNT,
        ) = AnalyzerPool(
            desiredAnalyzerCount = desiredAnalyzerCount,
            analyzers = (0 until desiredAnalyzerCount).mapNotNull { analyzerFactory.newInstance() }
        )
    }
}
