package com.getbouncer.scan.camera

import android.util.Size
import android.view.Surface
import androidx.test.filters.SmallTest
import org.junit.Test
import kotlin.test.assertEquals

class CameraAdapterInstrumentationTest {

    @Test
    @SmallTest
    fun sizeToResolution_vertical() {
        assertEquals(Size(120, 60), CameraAdapter.sizeToResolution(Size(60, 120)))
        assertEquals(Size(60, 60), CameraAdapter.sizeToResolution(Size(60, 60)))
        assertEquals(Size(1, -1), CameraAdapter.sizeToResolution(Size(-1, 1)))
    }

    @Test
    @SmallTest
    fun sizeToResolution_horizontal() {
        assertEquals(Size(120, 60), CameraAdapter.sizeToResolution(Size(120, 60)))
        assertEquals(Size(60, 60), CameraAdapter.sizeToResolution(Size(60, 60)))
        assertEquals(Size(1, -1), CameraAdapter.sizeToResolution(Size(1, -1)))
    }

    @Test
    @SmallTest
    fun resolutionToSize_perpendicular() {
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_0, 90))
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_0, 270))
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_90, 0))
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_90, 180))
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_180, 90))
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_180, 270))
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_270, 0))
        assertEquals(Size(60, 120), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_270, 180))
    }

    @Test
    @SmallTest
    fun resolutionToSize_parallel() {
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_0, 0))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_0, 180))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_90, 90))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_90, 270))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_180, 0))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_180, 180))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_270, 90))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_270, 270))
    }

    @Test
    @SmallTest
    fun resolutionToSize_oblique() {
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_0, 45))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_0, 135))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_90, 45))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_90, 135))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_180, 45))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_180, 135))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_270, 45))
        assertEquals(Size(120, 60), CameraAdapter.resolutionToSize(Size(120, 60), Surface.ROTATION_270, 135))
    }
}
