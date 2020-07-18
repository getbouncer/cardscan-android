package com.getbouncer.scan.framework.util

import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.annotation.CheckResult
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Determine the maximum size of rectangle with a given aspect ratio (X/Y) that can fit inside the
 * specified area.
 *
 * For example, if the aspect ratio is 1/2 and the area is 2x2, the resulting rectangle would be
 * size 1x2 and look like this:
 * ```
 *  ________
 * | |    | |
 * | |    | |
 * | |    | |
 * |_|____|_|
 * ```
 */
@CheckResult
fun maxAspectRatioInSize(area: Size, aspectRatio: Float): Size {
    var width = area.width
    var height = (width / aspectRatio).roundToInt()

    return if (height <= area.height) {
        Size(area.width, height)
    } else {
        height = area.height
        width = (height * aspectRatio).roundToInt()
        Size(min(width, area.width), height)
    }
}

/**
 * Calculate the position of the [Size] within the [containingSize]. This makes a few
 * assumptions:
 * 1. the [Size] and the [containingSize] are centered relative to each other.
 * 2. the [Size] and the [containingSize] have the same orientation
 * 3. the [containingSize] and the [Size] share either a horizontal or vertical field of view
 * 4. the non-shared field of view must be smaller on the [Size] than the [containingSize]
 *
 * Note that the [Size] and the [containingSize] are allowed to have completely independent
 * resolutions.
 */
@CheckResult
fun Size.scaleAndCenterWithin(containingSize: Size): Rect {
    val aspectRatio = width.toFloat() / height

    // Since the preview image may be at a different resolution than the full image, scale the
    // preview image to be circumscribed by the fullImage.
    val scaledSize = maxAspectRatioInSize(containingSize, aspectRatio)
    val left = (containingSize.width - scaledSize.width) / 2
    val top = (containingSize.height - scaledSize.height) / 2
    return Rect(
        /* left */ left,
        /* top */ top,
        /* right */ left + scaledSize.width,
        /* bottom */ top + scaledSize.height
    )
}

fun RectF.scaled(scaledSize: Size): RectF {
    return RectF(
        this.left * scaledSize.width,
        this.top * scaledSize.height,
        this.right * scaledSize.width,
        this.bottom * scaledSize.height
    )
}

fun RectF.centerScaled(scaleX: Float, scaleY: Float): RectF {
    return RectF(
        this.centerX() - this.width() * scaleX / 2,
        this.centerY() - this.height() * scaleY / 2,
        this.centerX() + this.width() * scaleX / 2,
        this.centerY() + this.height() * scaleY / 2
    )
}

fun Rect.size() = Size(width(), height())
