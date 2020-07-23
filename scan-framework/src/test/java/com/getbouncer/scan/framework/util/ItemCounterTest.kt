package com.getbouncer.scan.framework.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals

class ItemCounterTest {

    @Test
    @ExperimentalCoroutinesApi
    fun countItems() = runBlockingTest {
        val itemCounter = ItemCounter<String>()

        assertEquals(1, itemCounter.countItem("a"))
        assertEquals(1, itemCounter.countItem("b"))
        assertEquals(2, itemCounter.countItem("a"))

        assertEquals("a", itemCounter.getHighestCountItem()?.second)
        assertEquals(2, itemCounter.getHighestCountItem()?.first)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun reset() = runBlockingTest {
        val itemCounter = ItemCounter<String>()

        assertEquals(1, itemCounter.countItem("a"))
        assertEquals(1, itemCounter.countItem("b"))
        assertEquals(2, itemCounter.countItem("a"))

        itemCounter.reset()

        assertEquals(1, itemCounter.countItem("a"))
        assertEquals(1, itemCounter.countItem("b"))
        assertEquals(2, itemCounter.countItem("a"))
    }
}
