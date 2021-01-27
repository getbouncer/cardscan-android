package com.getbouncer.cardscan.ui.local.result

import androidx.test.filters.LargeTest
import com.getbouncer.scan.framework.time.delay
import com.getbouncer.scan.framework.time.milliseconds
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.ml.SSDOcr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainLoopStateMachineTest {

    @Test
    @ExperimentalCoroutinesApi
    fun initial_noOcr() = runBlockingTest {
        val state = MainLoopState.Initial()

        val prediction = SSDOcr.Prediction(
            pan = "",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Initial)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun initial_foundOcr() = runBlockingTest {
        val state = MainLoopState.Initial()

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.PanFound)
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    @ExperimentalCoroutinesApi
    fun panFound_noTimeout() = runBlockingTest {
        val state = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.PanFound)
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    @ExperimentalCoroutinesApi
    fun panFound_panSatisfied_noTimeout() = runBlockingTest {
        var state: MainLoopState = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        repeat(DESIRED_PAN_AGREEMENT - 2) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopState.PanFound)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished)
        assertEquals("4847186095118770", newState.pan)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun panFound_totalTimeout() = runBlocking {
        val state = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        delay(TOTAL_SCAN_DURATION + 1.milliseconds)

        val prediction = SSDOcr.Prediction(
            pan = "",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun panFound_desiredTimeout() = runBlocking {
        var state: MainLoopState = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        repeat(MINIMUM_PAN_AGREEMENT - 1) {
            state = state.consumeTransition(
                SSDOcr.Prediction(
                    pan = "4847186095118770",
                    detectedBoxes = emptyList(),
                )
            )
            assertTrue(state is MainLoopState.PanFound)
        }

        val prediction = SSDOcr.Prediction(
            pan = "",
            detectedBoxes = emptyList(),
        )

        delay(PAN_SEARCH_DURATION + 1.milliseconds)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun finished_goesNowhere() = runBlockingTest {
        val state = MainLoopState.Finished(
            pan = "4847186095118770",
        )

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertSame(state, newState)
    }
}
