package com.getbouncer.scan.payment.ml

import androidx.core.graphics.drawable.toBitmap
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.UpdatingResourceFetcher
import com.getbouncer.scan.framework.image.size
import com.getbouncer.scan.framework.util.toRect
import com.getbouncer.scan.payment.minimal.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CardDetectTest {
    private val appContext = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext = InstrumentationRegistry.getInstrumentation().context

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
        val fetcher = CardDetectModelManager.getModelFetcher(appContext)
        assertNotNull(fetcher)
        assertTrue(fetcher is UpdatingResourceFetcher)
        fetcher.clearCache()

        val fetchedData = fetcher.fetchData(forImmediateUse = true, isOptional = false) // use `forImmediateUse = true` to force the hash to match
        assertEquals(fetcher.hash, fetchedData.modelHash)

        val model = CardDetect.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            CardDetect.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size().toRect(),
                bitmap.size().toRect(),
            ),
            Unit
        )
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
        val fetcher = CardDetectModelManager.getModelFetcher(appContext)
        assertNotNull(fetcher)
        assertTrue(fetcher is UpdatingResourceFetcher)
        fetcher.clearCache()

        val fetchedData = fetcher.fetchData(forImmediateUse = true, isOptional = false) // use `forImmediateUse = true` to force the hash to match
        assertEquals(fetcher.hash, fetchedData.modelHash)

        val model = CardDetect.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            CardDetect.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size().toRect(),
                bitmap.size().toRect(),
            ),
            Unit
        )
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
        val fetcher = CardDetectModelManager.getModelFetcher(appContext)
        assertNotNull(fetcher)
        assertTrue(fetcher is UpdatingResourceFetcher)
        fetcher.clearCache()

        val fetchedData = fetcher.fetchData(forImmediateUse = true, isOptional = false) // use `forImmediateUse = true` to force the hash to match
        assertEquals(fetcher.hash, fetchedData.modelHash)

        val model = CardDetect.Factory(appContext, fetchedData).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(
            CardDetect.cameraPreviewToInput(
                TrackedImage(bitmap, Stats.trackTask("no_op")),
                bitmap.size().toRect(),
                bitmap.size().toRect(),
            ),
            Unit
        )
        assertNotNull(prediction)
        assertEquals(CardDetect.Prediction.Side.NO_CARD, prediction.side)
    }
}
