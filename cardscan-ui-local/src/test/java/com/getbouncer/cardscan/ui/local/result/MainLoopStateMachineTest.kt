package com.getbouncer.cardscan.ui.local.result

import androidx.test.filters.LargeTest
import com.getbouncer.scan.framework.time.delay
import com.getbouncer.scan.framework.time.milliseconds
import com.getbouncer.scan.framework.util.ItemTotalCounter
import com.getbouncer.scan.payment.ml.CardDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MainLoopStateMachineTest {

    @Test
    fun initial_runsOcrOnly() {
        val state = MainLoopState.Initial()

        assertTrue(state.runOcr)
        assertFalse(state.runCardDetect)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun initial_noCard_noOcr() = runBlockingTest {
        val state = MainLoopState.Initial()

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = null,
            card = null,
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Initial)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun initial_noCard_foundOcr() = runBlockingTest {
        val state = MainLoopState.Initial()

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = SSDOcr.Prediction(
                pan = "4847186095118770",
                detectedBoxes = emptyList(),
            ),
            card = null,
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.PanFound)
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    fun panFound_runsCardDetectAndOcrOnly() {
        val state = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        assertTrue(state.runOcr)
        assertTrue(state.runCardDetect)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun panFound_noCard_noTimeout() = runBlockingTest {
        val state = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = SSDOcr.Prediction(
                pan = "4847186095118770",
                detectedBoxes = emptyList(),
            ),
            card = null,
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.PanFound)
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    @ExperimentalCoroutinesApi
    fun panFound_cardSatisfied_noTimeout() = runBlockingTest {
        var state: MainLoopState = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = null,
            card = CardDetect.Prediction(
                side = CardDetect.Prediction.Side.PAN,
                panProbability = 1.0F,
                noPanProbability = 0.0F,
                noCardProbability = 0.0F,
            ),
        )

        repeat(DESIRED_SIDE_COUNT - 1) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopState.PanFound)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.CardSatisfied)
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    @ExperimentalCoroutinesApi
    fun panFound_panSatisfied_noTimeout() = runBlockingTest {
        var state: MainLoopState = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = SSDOcr.Prediction(
                pan = "4847186095118770",
                detectedBoxes = emptyList(),
            ),
            card = null,
        )

        repeat(DESIRED_PAN_AGREEMENT - 2) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopState.PanFound)
        }

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.PanSatisfied)
        assertEquals("4847186095118770", newState.pan)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @ExperimentalCoroutinesApi
    fun panFound_panSatisfied_timeout() = runBlocking {
        var state: MainLoopState = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = SSDOcr.Prediction(
                pan = "4847186095118770",
                detectedBoxes = emptyList(),
            ),
            card = null,
        )

        repeat(MINIMUM_PAN_AGREEMENT - 2) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopState.PanFound)
        }

        delay(PAN_SEARCH_DURATION + 1.milliseconds)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.PanSatisfied)
        assertEquals("4847186095118770", newState.pan)
    }

    /**
     * This test cannot use `runBlockingTest` because it requires a delay. While runBlockingTest
     * advances the dispatcher's virtual time by the specified amount, it does not affect the timing
     * of the duration.
     */
    @Test
    @LargeTest
    fun panFound_timeout() = runBlocking {
        val state = MainLoopState.PanFound(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        delay(PAN_AND_CARD_SEARCH_DURATION + 1.milliseconds)

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = null,
            card = null,
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished, "$newState is not Finished")
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    fun panSatisfied_runsCardDetectOnly() {
        val state = MainLoopState.PanSatisfied(
            pan = "4847186095118770",
            visibleCardCount = 0,
        )

        assertFalse(state.runOcr)
        assertTrue(state.runCardDetect)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun panSatisfied_noCard_noTimeout() = runBlockingTest {
        val state = MainLoopState.PanSatisfied(
            pan = "4847186095118770",
            visibleCardCount = 0,
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = null,
            card = CardDetect.Prediction(
                side = CardDetect.Prediction.Side.PAN,
                panProbability = 1.0F,
                noPanProbability = 0.0F,
                noCardProbability = 0.0F,
            ),
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.PanSatisfied)
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun panSatisfied_enoughSides_noTimeout() = runBlockingTest {
        val state = MainLoopState.PanSatisfied(
            pan = "4847186095118770",
            visibleCardCount = DESIRED_SIDE_COUNT - 1,
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = null,
            card = CardDetect.Prediction(
                side = CardDetect.Prediction.Side.PAN,
                panProbability = 1.0F,
                noPanProbability = 0.0F,
                noCardProbability = 0.0F,
            ),
        )

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
    fun panSatisfied_timeout() = runBlocking {
        val state = MainLoopState.PanSatisfied(
            pan = "4847186095118770",
            visibleCardCount = DESIRED_SIDE_COUNT - 1,
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = null,
            card = null,
        )

        delay(PAN_SEARCH_DURATION + 1.milliseconds)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished)
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    fun cardSatisfied_runsOcrOnly() {
        val state = MainLoopState.CardSatisfied(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        assertTrue(state.runOcr)
        assertFalse(state.runCardDetect)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun cardSatisfied_noPan_noTimeout() = runBlockingTest {
        val state = MainLoopState.CardSatisfied(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = SSDOcr.Prediction(
                pan = "4847186095118770",
                detectedBoxes = emptyList(),
            ),
            card = null,
        )

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.CardSatisfied)
        assertEquals("4847186095118770", newState.getMostLikelyPan())
    }

    @Test
    @ExperimentalCoroutinesApi
    fun cardSatisfied_pan_noTimeout() = runBlockingTest {
        var state: MainLoopState = MainLoopState.CardSatisfied(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = SSDOcr.Prediction(
                pan = "4847186095118770",
                detectedBoxes = emptyList(),
            ),
            card = null,
        )

        repeat(DESIRED_PAN_AGREEMENT - 2) {
            state = state.consumeTransition(prediction)
            assertTrue(state is MainLoopState.CardSatisfied)
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
    fun cardSatisfied_noPan_timeout() = runBlocking {
        val state = MainLoopState.CardSatisfied(
            panCounter = ItemTotalCounter("4847186095118770"),
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = null,
            card = null,
        )

        delay(PAN_SEARCH_DURATION + 1.milliseconds)

        val newState = state.consumeTransition(prediction)
        assertTrue(newState is MainLoopState.Finished)
        assertEquals("4847186095118770", newState.pan)
    }

    @Test
    fun finished_runsNothing() {
        val state = MainLoopState.Finished(
            pan = "4847186095118770",
        )

        assertFalse(state.runOcr)
        assertFalse(state.runCardDetect)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun finished_goesNowhere() = runBlockingTest {
        val state = MainLoopState.Finished(
            pan = "4847186095118770",
        )

        val prediction = MainLoopAnalyzer.Prediction(
            ocr = SSDOcr.Prediction(
                pan = "4847186095118770",
                detectedBoxes = emptyList(),
            ),
            card = CardDetect.Prediction(
                side = CardDetect.Prediction.Side.NO_CARD,
                panProbability = 0.0F,
                noPanProbability = 0.0F,
                noCardProbability = 1.0F,
            ),
        )

        val newState = state.consumeTransition(prediction)
        assertSame(state, newState)
    }
}
