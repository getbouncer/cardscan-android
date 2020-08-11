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
 * Calculate the maximum [Size] that fits within the [containingSize] and maintains the same aspect ratio as the subject
 * of this method. This is often used to project a preview image onto a full camera image.
 *
 * If using this to project a preview image onto a full camera image, This makes a few assumptions:
 * 1. the preview image [Size] and the full image [containingSize] are centered relative to each other
 * 2. the preview image and the full image have the same orientation
 * 3. the preview image and the full image share either a horizontal or vertical field of view
 * 4. the non-shared field of view must be smaller on the preview image than the full image
 *
 * Note that the [Size] and the [containingSize] are allowed to have completely independent resolutions.
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

/**
 * Center a size on a given rectangle. The size may be larger or smaller than the rect.
 */
@CheckResult
fun Size.centerOn(rect: Rect) = Rect(
    /* left */ rect.centerX() - this.width / 2,
    /* top */ rect.centerY() - this.height / 2,
    /* right */ rect.centerX() + this.width / 2,
    /* bottom */ rect.centerY() + this.height / 2
)

/**
 * Scale a [Rect] to have a size equivalent to the [scaledSize]. This will also scale the position of the [Rect].
 *
 * For example, scaling a Rect(1, 2, 3, 4) by Size(5, 6) will result in a Rect(5, 12, 15, 24)
 */
@CheckResult
fun RectF.scaled(scaledSize: Size) = RectF(
    this.left * scaledSize.width,
    this.top * scaledSize.height,
    this.right * scaledSize.width,
    this.bottom * scaledSize.height
)

/**
 * Scale a [Rect] to have a size equivalent to the [scaledSize]. This will maintain the center position of the [Rect].
 *
 * For example, scaling a Rect(5, 6, 7, 8) by Size(2, 0.5) will result
 */
@CheckResult
fun RectF.centerScaled(scaleX: Float, scaleY: Float) = RectF(
    this.centerX() - this.width() * scaleX / 2,
    this.centerY() - this.height() * scaleY / 2,
    this.centerX() + this.width() * scaleX / 2,
    this.centerY() + this.height() * scaleY / 2
)

@CheckResult
fun Rect.size() = Size(width(), height())
