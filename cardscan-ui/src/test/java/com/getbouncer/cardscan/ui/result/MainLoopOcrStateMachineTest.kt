package com.getbouncer.cardscan.ui.result

import androidx.test.filters.LargeTest
import com.getbouncer.scan.payment.ml.SSDOcr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainLoopOcrStateMachineTest {

    @Test
    @ExperimentalCoroutinesApi
    fun initial_noCardFound() = runBlockingTest {
        val state = MainLoopOcrState.Initial(enableNameExpiryExtraction = true)

        val prediction = SSDOcr.Prediction(
            pan = "",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)

        assertEquals(state, newState, "$state expected, got $newState")
    }

    @Test
    @ExperimentalCoroutinesApi
    fun initial_cardFound() = runBlockingTest {
        val state = MainLoopOcrState.Initial(enableNameExpiryExtraction = true)

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)

        assertTrue(newState is MainLoopOcrState.OcrRunning, "$newState is not NameAndExpiryRunning")
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_noCardFound_noTimeout_noAgreement() = runBlockingTest {
        val state = MainLoopOcrState.OcrRunning(
            firstPan = "4847186095118770",
            enableNameExpiryExtraction = true,
        )

        val prediction = SSDOcr.Prediction(
            pan = "",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)

        assertEquals(state, newState)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_cardAgreement_nameAndExpiry() = runBlockingTest {
        var state: MainLoopOcrState = MainLoopOcrState.OcrRunning(
            firstPan = "4847186095118770",
            enableNameExpiryExtraction = true,
        )

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        repeat(DESIRED_PAN_AGREEMENT - 2) { // -2 because of the `firstPan` above and to get to a state right before the transition occurs
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopOcrState.OcrRunning)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopOcrState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_cardAgreement_noNameNorExpiry() = runBlockingTest {
        var state: MainLoopOcrState = MainLoopOcrState.OcrRunning(
            firstPan = "4847186095118770",
            enableNameExpiryExtraction = false,
        )

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        repeat(DESIRED_PAN_AGREEMENT - 2) { // -2 because of the `firstPan` above and to get to a state right before the transition occurs
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopOcrState.OcrRunning)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopOcrState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_cardAgreement_multiplePansFound() = runBlockingTest {
        var state: MainLoopOcrState = MainLoopOcrState.OcrRunning(
            firstPan = "4847186095118770",
            enableNameExpiryExtraction = true,
        )

        val prediction1 = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        val prediction2 = SSDOcr.Prediction(
            pan = "5113320146845016",
            detectedBoxes = emptyList(),
        )

        repeat(DESIRED_PAN_AGREEMENT - 1) { // does not match initial, so we can go one more
            state = state.consumeTransition(prediction2)
            assertTrue(state is MainLoopOcrState.OcrRunning)
        }

        repeat(DESIRED_PAN_AGREEMENT - 2) { // -2 because of the `firstPan` above and to get to a state right before the transition occurs
            state = state.consumeTransition(prediction1)
            assertTrue(state is MainLoopOcrState.OcrRunning)
        }

        val newState = state.consumeTransition(prediction1)
        assertTrue(newState is MainLoopOcrState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun ocrRunning_timeElapsed_nameAndExpiry() = runBlocking {
        val state = MainLoopOcrState.OcrRunning(
            firstPan = "4847186095118770",
            enableNameExpiryExtraction = true,
        )

        delay(OCR_TIMEOUT_WITH_NAME_AND_EXPIRY.inMilliseconds.toLong() + 1)

        val prediction = SSDOcr.Prediction(
            pan = "",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopOcrState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun ocrRunning_timeElapsed_noNameNorExpiry() = runBlocking {
        val state = MainLoopOcrState.OcrRunning(
            firstPan = "4847186095118770",
            enableNameExpiryExtraction = false,
        )

        delay(OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY.inMilliseconds.toLong() + 1)

        val prediction = SSDOcr.Prediction(
            pan = "",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopOcrState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun finished_goesNowhere() = runBlockingTest {
        val state = MainLoopOcrState.Finished("4847186095118770")

        val prediction = SSDOcr.Prediction(
            pan = "4847186095118770",
            detectedBoxes = emptyList(),
        )

        val newState = state.consumeTransition(prediction)
        assertSame(state, newState)
    }
}
