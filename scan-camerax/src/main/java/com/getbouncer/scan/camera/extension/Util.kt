package com.getbouncer.scan.camera.extension

import android.util.Size
import kotlin.math.max
import kotlin.math.min

/**
 * Convert a resolution to a size on the screen based only on the display size.
 */
internal fun Size.resolutionToSize(displaySize: Size) = when {
    displaySize.width >= displaySize.height -> Size(
        /* width */
        max(width, height),
        /* height */
        min(width, height),
    )
    else -> Size(
        /* width */
        min(width, height),
        /* height */
        max(width, height),
    )
}
