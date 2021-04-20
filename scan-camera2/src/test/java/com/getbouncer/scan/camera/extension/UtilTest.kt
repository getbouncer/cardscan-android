package com.getbouncer.scan.camera.extension

import android.view.Surface
import androidx.test.filters.SmallTest
import org.junit.Test
import kotlin.test.assertEquals

class UtilTest {

    @Test
    @SmallTest
    fun calculateImageRotationDegrees_vertical() {
        assertEquals(0, calculateImageRotationDegrees(Surface.ROTATION_0, 0))
        assertEquals(45, calculateImageRotationDegrees(Surface.ROTATION_0, 45))
        assertEquals(315, calculateImageRotationDegrees(Surface.ROTATION_0, -45))
        assertEquals(180, calculateImageRotationDegrees(Surface.ROTATION_0, 180))
        assertEquals(180, calculateImageRotationDegrees(Surface.ROTATION_0, -180))
    }

    @Test
    @SmallTest
    fun calculateImageRotationDegrees_right() {
        assertEquals(270, calculateImageRotationDegrees(Surface.ROTATION_90, 0))
        assertEquals(315, calculateImageRotationDegrees(Surface.ROTATION_90, 45))
        assertEquals(225, calculateImageRotationDegrees(Surface.ROTATION_90, -45))
        assertEquals(90, calculateImageRotationDegrees(Surface.ROTATION_90, 180))
        assertEquals(90, calculateImageRotationDegrees(Surface.ROTATION_90, -180))
    }

    @Test
    @SmallTest
    fun calculateImageRotationDegrees_left() {
        assertEquals(90, calculateImageRotationDegrees(Surface.ROTATION_270, 0))
        assertEquals(135, calculateImageRotationDegrees(Surface.ROTATION_270, 45))
        assertEquals(45, calculateImageRotationDegrees(Surface.ROTATION_270, -45))
        assertEquals(270, calculateImageRotationDegrees(Surface.ROTATION_270, 180))
        assertEquals(270, calculateImageRotationDegrees(Surface.ROTATION_270, -180))
    }

    @Test
    @SmallTest
    fun calculateImageRotationDegrees_inverted() {
        assertEquals(180, calculateImageRotationDegrees(Surface.ROTATION_180, 0))
        assertEquals(225, calculateImageRotationDegrees(Surface.ROTATION_180, 45))
        assertEquals(135, calculateImageRotationDegrees(Surface.ROTATION_180, -45))
        assertEquals(0, calculateImageRotationDegrees(Surface.ROTATION_180, 180))
        assertEquals(0, calculateImageRotationDegrees(Surface.ROTATION_180, -180))
    }
}
