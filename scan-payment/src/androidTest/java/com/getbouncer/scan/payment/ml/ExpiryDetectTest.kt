package com.getbouncer.scan.payment.ml

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.Config
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertNotNull

class ExpiryDetectTest {
    private val testContext = InstrumentationRegistry.getInstrumentation().context

    @Before
    fun before() {
        Config.apiKey = "qOJ_fF-WLDMbG05iBq5wvwiTNTmM2qIn"
    }

    @After
    fun after() {
        Config.apiKey = null
    }

    /**
     * TODO: this method should use runBlockingTest instead of runBlocking. However, an issue with
     * runBlockingTest currently fails when functions under test use withContext(Dispatchers.IO) or
     * withContext(Dispatchers.Default).
     *
     * See https://github.com/Kotlin/kotlinx.coroutines/issues/1204 for details.
     */
    @Test
    @SmallTest
    fun createsInterpreter() = runBlocking {
        val fetcher = ExpiryDetect.ModelFetcher(testContext)
        fetcher.clearCache()

        val factory = ExpiryDetect.Factory(testContext, fetcher.fetchData(forImmediateUse = false, isOptional = false))

        assertNotNull(factory.newInstance())
    }.let { }

    @Test
    @SmallTest
    fun createsValidOutput() {
        // TODO: add resources and test the object detector
    }
}
