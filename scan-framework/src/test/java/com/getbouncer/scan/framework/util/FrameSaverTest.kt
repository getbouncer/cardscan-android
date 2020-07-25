package com.getbouncer.scan.framework.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals

class FrameSaverTest {

    private class TestFrameSaver : FrameSaver<String, Int>() {
        override fun getMaxSavedFrames(savedFrameIdentifier: String): Int = 3

        override fun getSaveFrameIdentifier(frame: Int): String? = when (frame) {
            1 -> "one"
            2 -> "two"
            else -> "else"
        }
    }

    @Test
    @ExperimentalCoroutinesApi
    fun saveFrames() = runBlockingTest {
        val frameSaver = TestFrameSaver()

        frameSaver.saveFrame(1)
        frameSaver.saveFrame(1)
        frameSaver.saveFrame(1)
        frameSaver.saveFrame(1)
        frameSaver.saveFrame(2)
        frameSaver.saveFrame(3)
        frameSaver.saveFrame(4)

        assertEquals(
            listOf(1, 1, 1),
            frameSaver.getSavedFrames()["one"]?.toList()
        )

        assertEquals(
            listOf(2),
            frameSaver.getSavedFrames()["two"]?.toList()
        )

        assertEquals(
            listOf(3, 4),
            frameSaver.getSavedFrames()["else"]?.toList()
        )
    }
}
