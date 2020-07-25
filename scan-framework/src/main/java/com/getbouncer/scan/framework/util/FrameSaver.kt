package com.getbouncer.scan.framework.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.LinkedList

/**
 * Save data frames for later retrieval.
 */
abstract class FrameSaver<Identifier, Frame> {

    private val saveFrameMutex = Mutex()
    private val savedFrames = mutableMapOf<Identifier, LinkedList<Frame>>()

    /**
     * Determine how frames should be classified using [getSaveFrameIdentifier], and then store them in a map of frames
     * based on that identifier.
     *
     * This method keeps track of the total number of saved frames. If the total number or total size exceeds the
     * maximum allowed, the oldest frames will be dropped.
     */
    suspend fun saveFrame(frame: Frame) {
        val savedFrameType = getSaveFrameIdentifier(frame) ?: return
        return saveFrameMutex.withLock {
            val maxSavedFrames = getMaxSavedFrames(savedFrameType)

            val typedSavedFrames = savedFrames.getOrPut(savedFrameType) { LinkedList() }
            typedSavedFrames.add(frame)

            while (typedSavedFrames.size > maxSavedFrames) {
                // saved frames is over size limit, reduce until it's not
                typedSavedFrames.removeFirst()
            }
        }
    }

    /**
     * Retrieve the list of saved frames.
     */
    fun getSavedFrames(): Map<Identifier, LinkedList<Frame>> = savedFrames

    /**
     * Clear all saved frames
     */
    suspend fun reset() = saveFrameMutex.withLock {
        savedFrames.clear()
    }

    protected abstract fun getMaxSavedFrames(savedFrameIdentifier: Identifier): Int

    /**
     * Determine if a data frame should be saved for future processing.
     *
     * If this method returns a non-null string, the frame will be saved under that identifier.
     */
    protected abstract fun getSaveFrameIdentifier(frame: Frame): Identifier?
}
