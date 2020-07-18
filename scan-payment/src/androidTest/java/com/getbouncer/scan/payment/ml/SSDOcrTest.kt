package com.getbouncer.scan.payment.ml

import android.graphics.Rect
import android.util.Size
import androidx.core.graphics.drawable.toBitmap
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.payment.size
import com.getbouncer.scan.payment.test.R
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SSDOcrTest {
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
    fun resourceModelExecution_works() = runBlocking {
        val bitmap = testContext.resources.getDrawable(R.drawable.ocr_card_numbers_clear, null).toBitmap()
        val model = SSDOcr.Factory(appContext, SSDOcr.ModelFetcher(appContext).fetchData(false)).newInstance()
        assertNotNull(model)

        val prediction = model.analyze(SSDOcr.Input(bitmap, bitmap.size(), bitmap.size().toRect(), Clock.markNow()), Unit)
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
        val model = SSDOcr.Factory(appContext, SSDOcr.ModelFetcher(appContext).fetchData(false)).newInstance()
        assertNotNull(model)

        val prediction1 = model.analyze(SSDOcr.Input(bitmap, bitmap.size(), bitmap.size().toRect(), Clock.markNow()), Unit)
        val prediction2 = model.analyze(SSDOcr.Input(bitmap, bitmap.size(), bitmap.size().toRect(), Clock.markNow()), Unit)
        assertNotNull(prediction1)
        assertEquals("4557095462268383", prediction1.pan)

        assertNotNull(prediction2)
        assertEquals("4557095462268383", prediction2.pan)
    }

    private fun Size.toRect(): Rect = Rect(0, 0, width, height)
}
