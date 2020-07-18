package com.getbouncer.scan.framework.api

import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.api.dto.AppInfo
import com.getbouncer.scan.framework.api.dto.BouncerErrorResponse
import com.getbouncer.scan.framework.api.dto.ClientDevice
import com.getbouncer.scan.framework.api.dto.ScanStatistics
import com.getbouncer.scan.framework.api.dto.StatsPayload
import com.getbouncer.scan.framework.util.AppDetails
import com.getbouncer.scan.framework.util.Device
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class BouncerApiTest {

    companion object {
        private const val STATS_PATH = "/scan_stats"
    }

    private val testContext = InstrumentationRegistry.getInstrumentation().context
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun before() {
        Config.apiKey = "uXDc2sbugrkmvj1Bm3xOTXBw7NW4llgn"
    }

    @After
    fun after() {
        Config.apiKey = null
    }

    @Test
    @LargeTest
    @ExperimentalCoroutinesApi
    fun uploadScanStats_success() = runBlockingTest {
        for (i in 0..100) {
            Stats.trackRepeatingTask("test_repeating_task_1").trackResult("$i")
        }

        for (i in 0..100) {
            Stats.trackRepeatingTask("test_repeating_task_2").trackResult("$i")
        }

        val task1 = Stats.trackTask("test_task_1")
        for (i in 0..5) {
            task1.trackResult("$i")
        }

        when (
            val result = postForResult(
                context = appContext,
                path = STATS_PATH,
                data = StatsPayload(
                    instanceId = "test_instance_id",
                    scanId = "test_scan_id",
                    device = ClientDevice.fromDevice(Device.fromContext(testContext)),
                    app = AppInfo.fromAppDetails(AppDetails.fromContext(testContext)),
                    scanStats = ScanStatistics.fromStats()
                ),
                requestSerializer = StatsPayload.serializer(),
                responseSerializer = ScanStatsResults.serializer(),
                errorSerializer = BouncerErrorResponse.serializer()
            )
        ) {
            is NetworkResult.Success -> {
                assertEquals(200, result.responseCode)
            }
            else -> fail("Network result was not success: $result")
        }
    }

    /**
     * TODO: this method should use runBlockingTest instead of runBlocking. However, an issue with
     * runBlockingTest currently fails when functions under test use withContext(Dispatchers.IO) or
     * withContext(Dispatchers.Default).
     *
     * See https://github.com/Kotlin/kotlinx.coroutines/issues/1204 for details.
     */
    @Test
    @LargeTest
    fun validateApiKey() = runBlocking {
        when (val result = validateApiKey(appContext)) {
            is NetworkResult.Success -> {
                assertEquals(200, result.responseCode)
                assertTrue(result.body.isApiKeyValid)
                assertNull(result.body.keyInvalidReason)
            }
            else -> fail("network result was not success: $result")
        }
    }

    /**
     * Note, if this test is failing with an unauthorized exception, please make sure that the API
     * key specified at the top of this file is authorized with DOWNLOAD_VERIFY_MODELS
     *
     *
     * TODO: this method should use runBlockingTest instead of runBlocking. However, an issue with
     * runBlockingTest currently fails when functions under test use withContext(Dispatchers.IO) or
     * withContext(Dispatchers.Default).
     *
     * See https://github.com/Kotlin/kotlinx.coroutines/issues/1204 for details.
     */
    @Test
    @LargeTest
    fun getModelSignedUrl() = runBlocking {
        when (
            val result = getModelSignedUrl(
                appContext,
                "fake_model",
                "v0.0.1",
                "model.tflite"
            )
        ) {
            is NetworkResult.Success -> {
                assertNotNull(result.body.modelUrl)
                assertNotEquals("", result.body.modelUrl)
            }
            else -> fail("network result was not success: $result")
        }
    }

    @Serializable
    data class ScanStatsResults(val status: String? = "")
}
