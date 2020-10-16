package com.getbouncer.scan.framework

import android.graphics.Bitmap

/**
 * An image from a camera with a stat tracker.
 */
data class TrackedCameraImage(
    val image: Bitmap,
    val tracker: StatTracker,
)
