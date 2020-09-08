package com.getbouncer.cardscan.ui

import androidx.test.filters.LargeTest
import com.getbouncer.cardscan.ui.analyzer.PaymentCardOcrAnalyzer
import com.getbouncer.cardscan.ui.result.DESIRED_EXPIRY_AGREEMENT
import com.getbouncer.cardscan.ui.result.DESIRED_NAME_AGREEMENT
import com.getbouncer.cardscan.ui.result.DESIRED_PAN_AGREEMENT
import com.getbouncer.cardscan.ui.result.EXPIRY_TIMEOUT
import com.getbouncer.cardscan.ui.result.MINIMUM_EXPIRY_AGREEMENT
import com.getbouncer.cardscan.ui.result.MINIMUM_NAME_AGREEMENT
import com.getbouncer.cardscan.ui.result.MainLoopState
import com.getbouncer.cardscan.ui.result.NAME_TIMEOUT
import com.getbouncer.cardscan.ui.result.OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY
import com.getbouncer.cardscan.ui.result.OCR_TIMEOUT_WITH_NAME_AND_EXPIRY
import com.getbouncer.scan.payment.ml.ExpiryDetect
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.math.max
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MainLoopStateMachineTest {

    @Test
    fun initial_runsOcrOnly() {
        val state = MainLoopState.Initial(enableNameExtraction = true, enableExpiryExtraction = true)
        assertTrue { state.runOcr }
        assertFalse { state.runNameExtraction }
        assertFalse { state.runExpiryExtraction }
    }

    @Test
    @ExperimentalCoroutinesApi
    fun initial_noCardFound() = runBlockingTest {
        val state = MainLoopState.Initial(enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        val newState = state.consumeTransition(prediction)

        assertEquals(state, newState, "$state expected, got $newState")
    }

    @Test
    @ExperimentalCoroutinesApi
    fun initial_cardFound() = runBlockingTest {
        val state = MainLoopState.Initial(enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = "4847186095118770",
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        val newState = state.consumeTransition(prediction)

        assertTrue(newState is MainLoopState.OcrRunning, "$newState is not NameAndExpiryRunning")
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    fun ocrRunning_runsOcrOnly() {
        val state = MainLoopState.OcrRunning(firstPan = "4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)
        assertTrue { state.runOcr }
        assertFalse { state.runExpiryExtraction }
        assertFalse { state.runNameExtraction }
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_noCardFound_noTimeout_noAgreement() = runBlockingTest {
        val state = MainLoopState.OcrRunning(firstPan = "4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        val newState = state.consumeTransition(prediction)

        assertEquals(state, newState)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_cardAgreement_nameAndExpiry() = runBlockingTest {
        val state = MainLoopState.OcrRunning(firstPan = "4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = "4847186095118770",
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(DESIRED_PAN_AGREEMENT - 2) { // -2 because of the `firstPan` above and to get to a state right before the transition occurs
            assertEquals(state, state.consumeTransition(prediction))
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.NameAndExpiryRunning, "$newState is not NameAndExpiryRunning")
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_cardAgreement_noNameNorExpiry() = runBlockingTest {
        val state = MainLoopState.OcrRunning(firstPan = "4847186095118770", enableNameExtraction = false, enableExpiryExtraction = false)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = "4847186095118770",
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(DESIRED_PAN_AGREEMENT - 2) { // -2 because of the `firstPan` above and to get to a state right before the transition occurs
            assertEquals(state, state.consumeTransition(prediction))
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertNull(newState.name)
        assertNull(newState.expiry)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun ocrRunning_cardAgreement_multiplePansFound() = runBlockingTest {
        val state = MainLoopState.OcrRunning(firstPan = "4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction1 = PaymentCardOcrAnalyzer.Prediction(
            pan = "4847186095118770",
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        val prediction2 = PaymentCardOcrAnalyzer.Prediction(
            pan = "5113320146845016",
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(DESIRED_PAN_AGREEMENT - 1) { // does not match initial, so we can go one more
            assertEquals(state, state.consumeTransition(prediction2))
        }

        repeat(DESIRED_PAN_AGREEMENT - 2) { // -2 because of the `firstPan` above and to get to a state right before the transition occurs
            assertEquals(state, state.consumeTransition(prediction1))
        }

        val newState = state.consumeTransition(prediction1)
        assertTrue(newState is MainLoopState.NameAndExpiryRunning, "$newState is not NameAndExpiryRunning")
        assertEquals("4847186095118770", (newState as MainLoopState.NameAndExpiryRunning).pan)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun ocrRunning_timeElapsed_nameAndExpiry() = runBlocking {
        val state = MainLoopState.OcrRunning(firstPan = "4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        delay(OCR_TIMEOUT_WITH_NAME_AND_EXPIRY.inMilliseconds.toLong() + 1)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.NameAndExpiryRunning, "$newState is not NameAndExpiryRunning")
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
        val state = MainLoopState.OcrRunning(firstPan = "4847186095118770", enableNameExtraction = false, enableExpiryExtraction = false)

        delay(OCR_TIMEOUT_WITHOUT_NAME_AND_EXPIRY.inMilliseconds.toLong() + 1)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertNull(newState.name)
        assertNull(newState.expiry)
    }

    @Test
    fun nameAndExpiryRunning_runsNameAndExpiryOnly() {
        val state1 = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)
        assertFalse(state1.runOcr)
        assertTrue(state1.runNameExtraction)
        assertTrue(state1.runExpiryExtraction)

        val state2 = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = false)
        assertFalse(state2.runOcr)
        assertTrue(state2.runNameExtraction)
        assertFalse(state2.runExpiryExtraction)

        val state3 = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = false, enableExpiryExtraction = true)
        assertFalse(state3.runOcr)
        assertFalse(state3.runNameExtraction)
        assertTrue(state3.runExpiryExtraction)

        val state4 = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = false, enableExpiryExtraction = false)
        assertFalse(state4.runOcr)
        assertFalse(state4.runNameExtraction)
        assertFalse(state4.runExpiryExtraction)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun nameAndExpiry_noName_noExpiry_noTimeout() = runBlockingTest {
        val state = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = null,
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        assertEquals(state, state.consumeTransition(prediction))
        assertNull(state.getMostLikelyName())
        assertNull(state.getMostLikelyExpiry())
    }

    @Test
    @ExperimentalCoroutinesApi
    fun nameAndExpiry_name_noExpiry_noTimeout() = runBlockingTest {
        val state = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = "some name",
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = false,
            isNameExtractionAvailable = true,
        )

        repeat(DESIRED_NAME_AGREEMENT - 1) {
            val tempState = state.consumeTransition(prediction)
            assertTrue(tempState is MainLoopState.NameAndExpiryRunning)
            assertFalse(tempState.runExpiryExtraction)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertEquals("some name", newState.name)
        assertNull(newState.expiry)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun nameAndExpiry_noName_expiry_noTimeout() = runBlockingTest {
        val state = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = null,
            expiry = ExpiryDetect.Expiry("00", "00"),
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = false,
        )

        repeat(DESIRED_EXPIRY_AGREEMENT - 1) {
            val tempState = state.consumeTransition(prediction)
            assertTrue(tempState is MainLoopState.NameAndExpiryRunning)
            assertFalse(tempState.runNameExtraction)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertNull(newState.name)
        assertEquals(ExpiryDetect.Expiry("00", "00"), newState.expiry)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun nameAndExpiry_name_expiry_noTimeout() = runBlockingTest {
        val state = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = "some name",
            expiry = ExpiryDetect.Expiry("00", "00"),
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(max(DESIRED_EXPIRY_AGREEMENT, DESIRED_NAME_AGREEMENT) - 1) {
            val tempState = state.consumeTransition(prediction)
            assertTrue(tempState is MainLoopState.NameAndExpiryRunning)

            if (it >= DESIRED_EXPIRY_AGREEMENT) {
                assertFalse(tempState.runExpiryExtraction)
            }

            if (it >= DESIRED_NAME_AGREEMENT) {
                assertFalse(tempState.runNameExtraction)
            }
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertEquals("some name", newState.name)
        assertEquals(ExpiryDetect.Expiry("00", "00"), newState.expiry)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun nameAndExpiry_expiryTimeout() = runBlocking {
        val state = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = false, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = null,
            expiry = ExpiryDetect.Expiry("00", "00"),
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(MINIMUM_EXPIRY_AGREEMENT - 1) {
            assertTrue(state.consumeTransition(prediction) is MainLoopState.NameAndExpiryRunning)
        }

        delay(EXPIRY_TIMEOUT.inMilliseconds.toLong() + 1)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertNull(newState.name)
        assertEquals(ExpiryDetect.Expiry("00", "00"), newState.expiry)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun nameAndExpiry_nameTimeout() = runBlocking {
        val state = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = false)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = "some name",
            expiry = null,
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(MINIMUM_NAME_AGREEMENT - 1) {
            assertTrue(state.consumeTransition(prediction) is MainLoopState.NameAndExpiryRunning)
        }

        delay(NAME_TIMEOUT.inMilliseconds.toLong() + 1)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertEquals("some name", newState.name)
        assertNull(newState.expiry)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun nameAndExpiry_nameAndExpiryTimeout() = runBlocking {
        val state = MainLoopState.NameAndExpiryRunning("4847186095118770", enableNameExtraction = true, enableExpiryExtraction = true)

        val prediction = PaymentCardOcrAnalyzer.Prediction(
            pan = null,
            panDetectionBoxes = null,
            name = "some name",
            expiry = ExpiryDetect.Expiry("00", "00"),
            objDetectionBoxes = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(max(MINIMUM_NAME_AGREEMENT, MINIMUM_EXPIRY_AGREEMENT) - 1) {
            val tempState = state.consumeTransition(prediction)
            assertTrue(tempState is MainLoopState.NameAndExpiryRunning)

            if (it >= DESIRED_EXPIRY_AGREEMENT) {
                assertFalse(tempState.runExpiryExtraction)
            }

            if (it >= DESIRED_NAME_AGREEMENT) {
                assertFalse(tempState.runNameExtraction)
            }
        }

        delay(com.getbouncer.scan.framework.time.max(NAME_TIMEOUT, EXPIRY_TIMEOUT).inMilliseconds.toLong() + 1)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
        assertEquals("some name", newState.name)
        assertEquals(ExpiryDetect.Expiry("00", "00"), newState.expiry)
    }
}
