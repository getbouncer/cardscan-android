package com.getbouncer.scan.ui

import android.graphics.RectF
import android.util.Size
import androidx.test.filters.SmallTest
import org.junit.Test
import kotlin.test.assertEquals

class DebugOverlayTest {

    @Test
    @SmallTest
    fun scaleRect() {
        assertEquals(RectF(2F, 4F, 6F, 8F), RectF(0.05F, 0.10F, 0.15F, 0.20F).scaled(Size(40, 40)))
    }
}
