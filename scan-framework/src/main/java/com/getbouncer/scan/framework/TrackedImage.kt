package com.getbouncer.scan.framework

import android.graphics.Bitmap

/**
 * An image with a stat tracker.
 */
data class TrackedImage(
    val image: Bitmap,
    val tracker: StatTracker,
)
