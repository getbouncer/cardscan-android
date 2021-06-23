package com.getbouncer.scan.payment.ml

import androidx.core.graphics.drawable.toBitmap
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.UpdatingModelWebFetcher
import com.getbouncer.scan.framework.UpdatingResourceFetcher
import com.getbouncer.scan.framework.image.size
import com.getbouncer.scan.framework.util.toRect
import com.getbouncer.scan.payment.test.R
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers, null).toBitmap()
        val fetcher = SSDOcrModelManager.getModelFetcher(appContext)
        assertNotNull(fetcher)
        assertFalse(fetcher is UpdatingResourceFetcher)
        assertTrue(fetcher is UpdatingModelWebFetcher)
        fetcher.clearCache()

        val model = SSDOcr.Factory(appContext, fetcher.fetchData(forImmediateUse = true, isOptional = false)).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size().toRect(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        assertNotNull(prediction)
        assertEquals("3023334877861104", prediction.pan)
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
    fun resourceModelExecution_worksWithQR() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers_qr, null).toBitmap()
        val fetcher = SSDOcrModelManager.getModelFetcher(appContext)
        assertNotNull(fetcher)
        assertFalse(fetcher is UpdatingResourceFetcher)
        assertTrue(fetcher is UpdatingModelWebFetcher)
        fetcher.clearCache()

        val model = SSDOcr.Factory(appContext, fetcher.fetchData(forImmediateUse = true, isOptional = false)).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size().toRect(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        assertNotNull(prediction)
        assertEquals("4242424242424242", prediction.pan)
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
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers, null).toBitmap()
        val fetcher = SSDOcrModelManager.getModelFetcher(appContext)
        assertNotNull(fetcher)
        assertFalse(fetcher is UpdatingResourceFetcher)
        assertTrue(fetcher is UpdatingModelWebFetcher)
        fetcher.clearCache()

        val model = SSDOcr.Factory(appContext, fetcher.fetchData(forImmediateUse = true, isOptional = false)).newInstance()
        assertNotNull(model)

        val prediction1 = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size().toRect(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        val prediction2 = model.analyze(
            SSDOcr.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size().toRect(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        assertNotNull(prediction1)
        assertEquals("3023334877861104", prediction1.pan)

        assertNotNull(prediction2)
        assertEquals("3023334877861104", prediction2.pan)
    }
}
