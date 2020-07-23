package com.getbouncer.scan.framework.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A class that counts and saves items.
 */
class ItemCounter<T> {
    private val storageMutex = Mutex()
    private val items = mutableMapOf<T, Int>()

    /**
     * Increment the count for the given item. Return the new count for the given item.
     */
    suspend fun countItem(field: T): Int = storageMutex.withLock {
        1 + (items.put(field, 1 + (items[field] ?: 0)) ?: 0)
    }

    /**
     * Get the item that with the highest count.
     *
     * @param minCount the minimum times an item must have been counted.
     */
    fun getHighestCountItem(minCount: Int = 1): Pair<Int, T>? =
        items.maxBy { it.value }?.let { if (items[it.key] ?: 0 >= minCount) it.value to it.key else null }

    /**
     * Reset all item counts.
     */
    suspend fun reset() = storageMutex.withLock {
        items.clear()
    }
}
