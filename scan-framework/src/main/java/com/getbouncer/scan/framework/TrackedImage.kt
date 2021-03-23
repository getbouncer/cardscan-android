package com.getbouncer.scan.framework

/**
 * An image with a stat tracker.
 */
data class TrackedImage<ImageType>(
    val image: ImageType,
    val tracker: StatTracker,
)
