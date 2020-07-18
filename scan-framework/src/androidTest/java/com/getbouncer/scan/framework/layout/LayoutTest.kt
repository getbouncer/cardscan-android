package com.getbouncer.scan.framework.layout

import android.graphics.Rect
import android.util.Size
import androidx.test.filters.SmallTest
import com.getbouncer.scan.framework.util.maxAspectRatioInSize
import com.getbouncer.scan.framework.util.scaleAndCenterWithin
import org.junit.Test
import kotlin.test.assertEquals

class LayoutTest {

    @Test
    @SmallTest
    fun maxAspectRatioInSize_sameRatio() {
        // the same aspect ratio as the size
        assertEquals(Size(16, 9), maxAspectRatioInSize(Size(16, 9), 16.toFloat() / 9))
    }

    @Test
    @SmallTest
    fun maxAspectRatioInSize_wide() {
        // an aspect ratio that's wider than tall
        assertEquals(Size(16, 9), maxAspectRatioInSize(Size(16, 16), 16.toFloat() / 9))
    }

    @Test
    @SmallTest
    fun maxAspectRatioInSize_tall() {
        // an aspect ratio that's taller than wide
        assertEquals(Size(9, 16), maxAspectRatioInSize(Size(16, 16), 9.toFloat() / 16))
    }

    @Test
    @SmallTest
    fun scaleAndCenterWithin_horizontal() {
        // center horizontally
        assertEquals(Rect(5, 0, 20, 15), Size(4, 4).scaleAndCenterWithin(Size(25, 15)))
    }

    @Test
    @SmallTest
    fun scaleAndCenterWithin_vertical() {
        // center vertically
        assertEquals(Rect(0, 5, 15, 20), Size(4, 4).scaleAndCenterWithin(Size(15, 25)))
    }

    @Test
    @SmallTest
    fun scaleAndCenterWithin_sameSquare() {
        // same ratio
        assertEquals(Rect(0, 0, 15, 15), Size(4, 4).scaleAndCenterWithin(Size(15, 15)))
    }

    @Test
    @SmallTest
    fun scaleAndCenterWithin_sameRectangle() {
        // same ratio, not square
        assertEquals(Rect(0, 0, 25, 15), Size(5, 3).scaleAndCenterWithin(Size(25, 15)))
    }
}
