package com.getbouncer.scan.camera.extension

import android.util.Size
import android.view.Surface
import androidx.test.filters.SmallTest
import org.junit.Test
import kotlin.test.assertEquals

class UtilInstrumentationTest {

    @Test
    @SmallTest
    fun resolutionToSize_perpendicular() {
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_0, 90))
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_0, 270))
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_90, 0))
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_90, 180))
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_180, 90))
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_180, 270))
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_270, 0))
        assertEquals(Size(60, 120), Size(120, 60).resolutionToSize(Surface.ROTATION_270, 180))
    }

    @Test
    @SmallTest
    fun resolutionToSize_parallel() {
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_0, 0))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_0, 180))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_90, 90))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_90, 270))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_180, 0))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_180, 180))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_270, 90))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_270, 270))
    }

    @Test
    @SmallTest
    fun resolutionToSize_oblique() {
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_0, 45))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_0, 135))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_90, 45))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_90, 135))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_180, 45))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_180, 135))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_270, 45))
        assertEquals(Size(120, 60), Size(120, 60).resolutionToSize(Surface.ROTATION_270, 135))
    }
}
