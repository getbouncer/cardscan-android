package com.getbouncer.scan.framework

/**
 * The default number of analyzers to run in parallel.
 */
internal const val DEFAULT_ANALYZER_PARALLEL_COUNT = 4

/**
 * An analyzer takes some data as an input, and returns an analyzed output. Analyzers should not contain any state. They
 * must define whether they can run on a multithreaded executor, and provide a means of analyzing input data to return
 * some form of result.
 */
interface Analyzer<Input, State, Output> {
    val name: String

    suspend fun analyze(data: Input, state: State): Output
}

/**
 * A factory to create analyzers.
 */
interface AnalyzerFactory<Output : Analyzer<*, *, *>> {
    suspend fun newInstance(): Output?
}

/**
 * A pool of analyzers.
 */
data class AnalyzerPool<DataFrame, State, Output>(
    val desiredAnalyzerCount: Int,
    val analyzers: List<Analyzer<DataFrame, State, Output>>
)

/**
 * A pool of analyzers.
 */
class AnalyzerPoolFactory<DataFrame, State, Output> @JvmOverloads constructor(
    private val analyzerFactory: AnalyzerFactory<out Analyzer<DataFrame, State, Output>>,
    private val desiredAnalyzerCount: Int = DEFAULT_ANALYZER_PARALLEL_COUNT
) {
    suspend fun buildAnalyzerPool() = AnalyzerPool(
        desiredAnalyzerCount = desiredAnalyzerCount,
        analyzers = (0 until desiredAnalyzerCount).mapNotNull { analyzerFactory.newInstance() }
    )
}
