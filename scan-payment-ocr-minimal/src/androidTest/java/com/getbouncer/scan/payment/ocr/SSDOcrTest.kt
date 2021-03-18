package com.getbouncer.scan.payment.ocr

import androidx.core.graphics.drawable.toBitmap
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.util.toRect
import com.getbouncer.scan.payment.ocr.test.R
import com.getbouncer.scan.payment.size
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class SSDOcrTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
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
    @MediumTest
    fun resourceModelExecution_works() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        val fetcher = SSDOcr.ModelFetcher(appContext)
        fetcher.clearCache()
        val fetchedData = fetcher.fetchData(forImmediateUse = false, isOptional = false)

        assertEquals(fetcher.hash, fetchedData.modelHash)

        val model = SSDOcr.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        assertNotNull(prediction)
        assertEquals("4557095462268383", prediction.pan)
    }

    /**
     * TODO: this method should use runBlockingTest instead of runBlocking. However, an issue with
     * runBlockingTest currently fails when functions under test use withContext(Dispatchers.IO) or
     * withContext(Dispatchers.Default).
     *
     * See https://github.com/Kotlin/kotlinx.coroutines/issues/1204 for details.
     */
    @Test
    @MediumTest
    fun resourceModelExecution_worksRepeatedly() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        val fetcher = SSDOcr.ModelFetcher(appContext)
        fetcher.clearCache()
        val fetchedData = fetcher.fetchData(forImmediateUse = true, isOptional = false)

        assertEquals(fetcher.hash, fetchedData.modelHash)

        val model = SSDOcr.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction1 = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        val prediction2 = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        assertNotNull(prediction1)
        assertEquals("4557095462268383", prediction1.pan)

        assertNotNull(prediction2)
        assertEquals("4557095462268383", prediction2.pan)
    }

    @Test
    @MediumTest
    fun resourceModelExecution_download_works() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        val fetcher = SSDOcr.ModelFetcher(appContext)
        fetcher.clearCache()
        val fetchedData = fetcher.fetchData(forImmediateUse = false, isOptional = false)

        assertNotEquals(fetcher.defaultModelHash, fetchedData.modelHash)

        val model = SSDOcr.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        assertNotNull(prediction)
        assertEquals("4557095462268383", prediction.pan)
    }
}
