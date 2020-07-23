package com.getbouncer.scan.framework.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Test
import kotlin.test.assertEquals

class ItemCounterTest {

    @Test
    @ExperimentalCoroutinesApi
    fun countItems() = runBlockingTest {
        val itemCounter = ItemCounter<Int>()

        assertEquals(1, itemCounter.countItem(4))
        assertEquals(1, itemCounter.countItem(3))
        assertEquals(2, itemCounter.countItem(4))

        assertEquals(1, itemCounter.getHighestCountItem()?.second)
        assertEquals(4, itemCounter.getHighestCountItem()?.first)
    }

    @Test
    @ExperimentalCoroutinesApi
    fun reset() = runBlockingTest {
        val itemCounter = ItemCounter<Int>()

        assertEquals(1, itemCounter.countItem(4))
        assertEquals(1, itemCounter.countItem(3))
        assertEquals(2, itemCounter.countItem(4))

        itemCounter.reset()

        assertEquals(1, itemCounter.countItem(4))
        assertEquals(1, itemCounter.countItem(3))
        assertEquals(2, itemCounter.countItem(4))
    }
}
