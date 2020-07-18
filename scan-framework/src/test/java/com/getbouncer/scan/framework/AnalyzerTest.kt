package com.getbouncer.scan.framework

import androidx.test.filters.SmallTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals

class AnalyzerTest {

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun analyzerPoolCreateNormally() = runBlockingTest {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override suspend fun newInstance(): TestAnalyzer? = TestAnalyzer()
        }

        val analyzerPool = AnalyzerPoolFactory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(12, analyzerPool.analyzers.size)
        assertEquals(3, analyzerPool.analyzers[0].analyze(1, 2))
    }

    @Test
    @SmallTest
    @ExperimentalCoroutinesApi
    fun analyzerPoolCreateFailure() = runBlockingTest {
        class TestAnalyzerFactory : AnalyzerFactory<TestAnalyzer> {
            override suspend fun newInstance(): TestAnalyzer? = null
        }

        val analyzerPool = AnalyzerPoolFactory(
            analyzerFactory = TestAnalyzerFactory(),
            desiredAnalyzerCount = 12
        ).buildAnalyzerPool()

        assertEquals(12, analyzerPool.desiredAnalyzerCount)
        assertEquals(0, analyzerPool.analyzers.size)
    }

    private class TestAnalyzer : Analyzer<Int, Int, Int> {
        override val name: String = "TestAnalyzer"
        override suspend fun analyze(data: Int, state: Int): Int = data + state
    }
}
