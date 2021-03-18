package com.getbouncer.scan.payment.carddetect

import androidx.core.graphics.drawable.toBitmap
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.util.toRect
import com.getbouncer.scan.payment.carddetect.test.R
import com.getbouncer.scan.payment.size
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class CardDetectTest {
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
    fun cardDetect_pan() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.card_pan, null).toBitmap()
        val fetcher = CardDetect.ModelFetcher(appContext)
        fetcher.clearCache()
        val fetchedData = fetcher.fetchData(forImmediateUse = true, isOptional = false)

        assertEquals(fetcher.defaultModelHash, fetchedData.modelHash)

        val model = CardDetect.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(CardDetect.cameraPreviewToInput(TrackedImage(bitmap, Stats.trackTask("no_op")), bitmap.size(), bitmap.size().toRect()), Unit)
        assertNotNull(prediction)
        assertEquals(CardDetect.Prediction.Side.PAN, prediction.side)
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
    fun cardDetect_noPan() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.card_no_pan, null).toBitmap()
        val fetcher = CardDetect.ModelFetcher(appContext)
        fetcher.clearCache()
        val fetchedData = fetcher.fetchData(forImmediateUse = true, isOptional = false)

        assertEquals(fetcher.defaultModelHash, fetchedData.modelHash)

        val model = CardDetect.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(CardDetect.cameraPreviewToInput(TrackedImage(bitmap, Stats.trackTask("no_op")), bitmap.size(), bitmap.size().toRect()), Unit)
        assertNotNull(prediction)
        assertEquals(CardDetect.Prediction.Side.NO_PAN, prediction.side)
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
    fun cardDetect_noCard() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.card_no_card, null).toBitmap()
        val fetcher = CardDetect.ModelFetcher(appContext)
        fetcher.clearCache()
        val fetchedData = fetcher.fetchData(forImmediateUse = true, isOptional = false)

        assertEquals(fetcher.defaultModelHash, fetchedData.modelHash)

        val model = CardDetect.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(CardDetect.cameraPreviewToInput(TrackedImage(bitmap, Stats.trackTask("no_op")), bitmap.size(), bitmap.size().toRect()), Unit)
        assertNotNull(prediction)
        assertEquals(CardDetect.Prediction.Side.NO_CARD, prediction.side)
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
    fun cardDetect_download_pan() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.card_pan, null).toBitmap()
        val fetcher = CardDetect.ModelFetcher(appContext)
        fetcher.clearCache()
        val fetchedData = fetcher.fetchData(forImmediateUse = false, isOptional = false)

        assertNotEquals(fetcher.defaultModelHash, fetchedData.modelHash)

        val model = CardDetect.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(CardDetect.cameraPreviewToInput(TrackedImage(bitmap, Stats.trackTask("no_op")), bitmap.size(), bitmap.size().toRect()), Unit)
        assertNotNull(prediction)
        assertEquals(CardDetect.Prediction.Side.PAN, prediction.side)
    }
}
