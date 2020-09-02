package com.getbouncer.scan.framework.util

import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import androidx.annotation.CheckResult
import kotlin.math.max
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
 * Determine the minimum size of rectangle with a given aspect ratio (X/Y) that a specified area
 * can fit inside it.
 *
 * For example, if the aspect ratio is 1/2 and the area is 1x1, the resulting rectangle would be
 * size 1x2 and look like this:
 * ```
 *  ____
 * |____|
 * |    |
 * |____|
 * |____|
 * ```
 */
@CheckResult
fun minAspectRatioSurroundingSize(area: Size, aspectRatio: Float): Size {
    var width = area.width
    var height = (width / aspectRatio).roundToInt()

    return if (height >= area.height) {
        Size(area.width, height)
    } else {
        height = area.height
        width = (height * aspectRatio).roundToInt()
        Size(max(width, area.width), height)
    }
}

/**
 * Given a size and an aspect ratio, resize the area to fit that aspect ratio. If the desired aspect
 * ratio is smaller than the one of the provided size, the size will be cropped to match. If the
 * desired aspect ratio is larger than the that of the provided size, then the size will be expanded
 * to match.
 */
@CheckResult
fun adjustSizeToAspectRatio(area: Size, aspectRatio: Float): Size = if (aspectRatio < 1) {
    Size(area.width, (area.width / aspectRatio).roundToInt())
} else {
    Size((area.height * aspectRatio).roundToInt(), area.height)
}

/**
 * Calculate the position of the [Size] within the [containingSize]. This makes a few
 * assumptions:
 * 1. the [Size] and the [containingSize] are centered relative to each other.
 * 2. the [Size] and the [containingSize] have the same orientation
 * 3. the [containingSize] and the [Size] share either a horizontal or vertical field of view
 * 4. the non-shared field of view must be smaller on the [Size] than the [containingSize]
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
        /* left */
        left,
        /* top */
        top,
        /* right */
        left + scaledSize.width,
        /* bottom */
        top + scaledSize.height
    )
}

/**
 * Center a size on a given rectangle. The size may be larger or smaller than the rect.
 */
@CheckResult
fun Size.centerOn(rect: Rect) = Rect(
    /* left */
    rect.centerX() - this.width / 2,
    /* top */
    rect.centerY() - this.height / 2,
    /* right */
    rect.centerX() + this.width / 2,
    /* bottom */
    rect.centerY() + this.height / 2
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
fun Rect.centerScaled(scaleX: Float, scaleY: Float) = Rect(
    this.centerX() - (this.width() * scaleX / 2).toInt(),
    this.centerY() - (this.height() * scaleY / 2).toInt(),
    this.centerX() + (this.width() * scaleX / 2).toInt(),
    this.centerY() + (this.height() * scaleY / 2).toInt()
)

/**
 * Converts a size to rectangle with the top left corner at 0,0
 */
@CheckResult
fun Size.toRect() = Rect(0, 0, this.width, this.height)

/**
 * Return a rect that is the intersection of two other rects
 */
@CheckResult
fun Rect.intersectionWith(rect: Rect): Rect {
    require(this.intersect(rect)) {
        "Given rects do not intersect"
    }

    return Rect(
        max(this.left, rect.left),
        max(this.top, rect.top),
        min(this.right, rect.right),
        min(this.bottom, rect.bottom)
    )
}

/**
 * Move relative to its current position
 */
@CheckResult
fun Rect.move(relativeX: Int, relativeY: Int) = Rect(
    this.left + relativeX,
    this.top + relativeY,
    this.right + relativeX,
    this.bottom + relativeY
)

/**
 * Takes a relation between a region of interest and a size and projects the region of interest
 * to that new location
 */
@CheckResult
fun Size.projectRegionOfInterest(toSize: Size, regionOfInterest: Rect): Rect {
    require(this.width > 0 || this.height > 0) {
        "Cannot project from container with non-positive dimensions"
    }

    return Rect(
        regionOfInterest.left * toSize.width / this.width,
        regionOfInterest.top * toSize.height / this.height,
        regionOfInterest.right * toSize.width / this.width,
        regionOfInterest.bottom * toSize.height / this.height
    )
}

/**
 * Resizes a region of the image and places it somewhere else
 */
@CheckResult
fun Size.resizeRegion(
    originalCenterRect: Rect,
    toCenterRect: Rect,
    toImageSize: Size
): Map<Rect, Rect> = mapOf(
    Rect(
        0,
        0,
        originalCenterRect.left,
        originalCenterRect.top
    ) to Rect(
        0,
        0,
        toCenterRect.left,
        toCenterRect.top
    ),
    Rect(
        originalCenterRect.left,
        0,
        originalCenterRect.right,
        originalCenterRect.top
    ) to Rect(
        toCenterRect.left,
        0,
        toCenterRect.right,
        toCenterRect.top
    ),
    Rect(
        originalCenterRect.right,
        0,
        this.width,
        originalCenterRect.top
    ) to Rect(
        toCenterRect.right,
        0,
        toImageSize.width,
        toCenterRect.top
    ),
    Rect(
        0,
        originalCenterRect.top,
        originalCenterRect.left,
        originalCenterRect.bottom
    ) to Rect(
        0,
        toCenterRect.top,
        toCenterRect.left,
        toCenterRect.bottom
    ),
    Rect(
        originalCenterRect.left,
        originalCenterRect.top,
        originalCenterRect.right,
        originalCenterRect.bottom
    ) to Rect(
        toCenterRect.left,
        toCenterRect.top,
        toCenterRect.right,
        toCenterRect.bottom
    ),
    Rect(
        originalCenterRect.right,
        originalCenterRect.top,
        this.width,
        originalCenterRect.bottom
    ) to Rect(
        toCenterRect.right,
        toCenterRect.top,
        toImageSize.width,
        toCenterRect.bottom
    ),
    Rect(
        0,
        originalCenterRect.bottom,
        originalCenterRect.left,
        this.height
    ) to Rect(
        0,
        toCenterRect.bottom,
        toCenterRect.left,
        toImageSize.height
    ),
    Rect(
        originalCenterRect.left,
        originalCenterRect.bottom,
        originalCenterRect.right,
        this.height
    ) to Rect(
        toCenterRect.left,
        toCenterRect.bottom,
        toCenterRect.right,
        toImageSize.height
    ),
    Rect(
        originalCenterRect.right,
        originalCenterRect.bottom,
        this.width,
        this.height
    ) to Rect(
        toCenterRect.right,
        toCenterRect.bottom,
        toImageSize.width,
        toImageSize.height
    )
)

fun Rect.size() = Size(width(), height())

fun Size.aspectRatio() = width.toFloat() / height.toFloat()
