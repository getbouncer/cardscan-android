package com.getbouncer.cardscan.ui.result

import androidx.test.filters.LargeTest
import com.getbouncer.cardscan.ui.analyzer.MainLoopNameExpiryAnalyzer
import com.getbouncer.scan.framework.time.max
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
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainLoopNameExpiryStateMachineTest {

    @Test
    fun nameAndExpiryRunning_runsNameAndExpiryOnly() {
        val state1 = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = true,
        )
        assertTrue(state1.runNameExtraction)
        assertTrue(state1.runExpiryExtraction)

        val state2 = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = false,
        )
        assertTrue(state2.runNameExtraction)
        assertFalse(state2.runExpiryExtraction)

        val state3 = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = false,
            enableExpiryExtraction = true,
        )
        assertFalse(state3.runNameExtraction)
        assertTrue(state3.runExpiryExtraction)

        val state4 = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = false,
            enableExpiryExtraction = false,
        )
        assertFalse(state4.runNameExtraction)
        assertFalse(state4.runExpiryExtraction)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun nameAndExpiry_noName_noExpiry_noTimeout() = runBlockingTest {
        val state = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = true,
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = null,
            expiry = null,
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
        var state: MainLoopNameExpiryState = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = true,
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = "some name",
            expiry = null,
            isExpiryExtractionAvailable = false,
            isNameExtractionAvailable = true,
        )

        repeat(DESIRED_NAME_AGREEMENT - 1) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopNameExpiryState.NameAndExpiryRunning)
            val tempState = state as MainLoopNameExpiryState.NameAndExpiryRunning
            assertFalse(tempState.runExpiryExtraction)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopNameExpiryState.Finished, "$newState is not Finished")
        assertEquals("some name", newState.name)
        assertNull(newState.expiry)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun nameAndExpiry_noName_expiry_noTimeout() = runBlockingTest {
        var state: MainLoopNameExpiryState = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = true,
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = null,
            expiry = ExpiryDetect.Expiry("00", "00"),
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = false,
        )

        repeat(DESIRED_EXPIRY_AGREEMENT - 1) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopNameExpiryState.NameAndExpiryRunning)
            val tempState = state as MainLoopNameExpiryState.NameAndExpiryRunning
            assertFalse(tempState.runNameExtraction)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopNameExpiryState.Finished, "$newState is not Finished")
        assertNull(newState.name)
        assertEquals(ExpiryDetect.Expiry("00", "00"), newState.expiry)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun nameAndExpiry_name_expiry_noTimeout() = runBlockingTest {
        var state: MainLoopNameExpiryState = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = true,
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = "some name",
            expiry = ExpiryDetect.Expiry("00", "00"),
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(max(DESIRED_EXPIRY_AGREEMENT, DESIRED_NAME_AGREEMENT) - 1) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopNameExpiryState.NameAndExpiryRunning)
            val tempState = state as MainLoopNameExpiryState.NameAndExpiryRunning

            if (it >= DESIRED_EXPIRY_AGREEMENT) {
                assertFalse(tempState.runExpiryExtraction)
            }

            if (it >= DESIRED_NAME_AGREEMENT) {
                assertFalse(tempState.runNameExtraction)
            }
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopNameExpiryState.Finished, "$newState is not Finished")
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
        var state: MainLoopNameExpiryState = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = false,
            enableExpiryExtraction = true,
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = null,
            expiry = ExpiryDetect.Expiry("00", "00"),
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(MINIMUM_EXPIRY_AGREEMENT - 1) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopNameExpiryState.NameAndExpiryRunning)
        }

        delay(EXTRACT_EXPIRY_DURATION.inMilliseconds.toLong() + 1)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopNameExpiryState.Finished, "$newState is not Finished")
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
        var state: MainLoopNameExpiryState = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = false,
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = "some name",
            expiry = null,
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(MINIMUM_NAME_AGREEMENT - 1) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopNameExpiryState.NameAndExpiryRunning)
        }

        delay(EXTRACT_NAME_DURATION.inMilliseconds.toLong() + 1)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopNameExpiryState.Finished, "$newState is not Finished")
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
        var state: MainLoopNameExpiryState = MainLoopNameExpiryState.NameAndExpiryRunning(
            enableNameExtraction = true,
            enableExpiryExtraction = true,
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = "some name",
            expiry = ExpiryDetect.Expiry("00", "00"),
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        repeat(max(MINIMUM_NAME_AGREEMENT, MINIMUM_EXPIRY_AGREEMENT) - 1) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopNameExpiryState.NameAndExpiryRunning)
            val tempState = state as MainLoopNameExpiryState.NameAndExpiryRunning

            if (it >= DESIRED_EXPIRY_AGREEMENT) {
                assertFalse(tempState.runExpiryExtraction)
            }

            if (it >= DESIRED_NAME_AGREEMENT) {
                assertFalse(tempState.runNameExtraction)
            }
        }

        delay(max(EXTRACT_NAME_DURATION, EXTRACT_EXPIRY_DURATION).inMilliseconds.toLong() + 1)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopNameExpiryState.Finished, "$newState is not Finished")
        assertEquals("some name", newState.name)
        assertEquals(ExpiryDetect.Expiry("00", "00"), newState.expiry)
    }

    @Test
    fun finished_runsNothing() {
        val state = MainLoopNameExpiryState.Finished(
            name = "some name",
            expiry = ExpiryDetect.Expiry("00", "00"),
        )
        assertFalse(state.runNameExtraction)
        assertFalse(state.runExpiryExtraction)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun finished_goesNowhere() = runBlockingTest {
        val state = MainLoopNameExpiryState.Finished(
            name = "some name",
            expiry = ExpiryDetect.Expiry("00", "00"),
        )

        val prediction = MainLoopNameExpiryAnalyzer.Prediction(
            detectionBoxes = null,
            name = "some name",
            expiry = ExpiryDetect.Expiry("00", "00"),
            isExpiryExtractionAvailable = true,
            isNameExtractionAvailable = true,
        )

        val newState = state.consumeTransition(prediction)
        assertSame(state, newState)
    }
}
