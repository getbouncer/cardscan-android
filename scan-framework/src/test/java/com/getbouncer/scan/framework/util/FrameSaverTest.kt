package com.getbouncer.scan.framework.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals

class FrameSaverTest {

    private class TestFrameSaver : FrameSaver<String, Int, Boolean, Int>() {
        override fun getMaxSavedFrames(savedFrameIdentifier: String): Int = 3

        override fun getSaveFrameIdentifier(frame: Int, result: Int): String? = when (frame) {
            1 -> "one"
            2 -> "two"
            else -> "else"
        }
    }

    @Test
    @ExperimentalCoroutinesApi
    fun saveFrames() = runBlockingTest {
        val frameSaver = TestFrameSaver()

        frameSaver.saveFrame(1, true, 2)
        frameSaver.saveFrame(1, true, 3)
        frameSaver.saveFrame(1, false, 2)
        frameSaver.saveFrame(1, false, 3)
        frameSaver.saveFrame(2, true, 4)
        frameSaver.saveFrame(3, true, 5)
        frameSaver.saveFrame(4, false, 6)

        assertEquals(
            listOf(
                FrameSaver.SavedFrame(1, true, 3),
                FrameSaver.SavedFrame(1, false, 2),
                FrameSaver.SavedFrame(1, false, 3)
            ),
            frameSaver.getSavedFrames()["one"]?.toList()
        )

        assertEquals(
            listOf(FrameSaver.SavedFrame(2, true, 4)),
            frameSaver.getSavedFrames()["two"]?.toList()
        )

        assertEquals(
            listOf(
                FrameSaver.SavedFrame(3, true, 5),
                FrameSaver.SavedFrame(4, false, 6)
            ),
            frameSaver.getSavedFrames()["else"]?.toList()
        )
    }
}
