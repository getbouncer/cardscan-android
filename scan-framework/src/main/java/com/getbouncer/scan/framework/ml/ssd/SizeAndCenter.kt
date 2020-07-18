package com.getbouncer.scan.framework.ml.ssd

import com.getbouncer.scan.framework.util.clamp
import kotlin.math.exp

/**
 * An array of four floats, which denote a rectangle of the following values:
 * [0] = centerX percent
 * [1] = centerY percent
 * [2] = width percent
 * [3] = height percent
 */
typealias SizeAndCenter = FloatArray

const val SIZE_AND_CENTER_SIZE = 4

/**
 * Create a new SizeAndCenter.
 */
fun sizeAndCenter(centerX: Float, centerY: Float, width: Float, height: Float) =
    SizeAndCenter(SIZE_AND_CENTER_SIZE).apply {
        setCenterX(centerX)
        setCenterY(centerY)
        setWidth(width)
        setHeight(height)
    }

fun SizeAndCenter.centerX() = this[0]
fun SizeAndCenter.centerY() = this[1]
fun SizeAndCenter.width() = this[2]
fun SizeAndCenter.height() = this[3]

fun SizeAndCenter.setCenterX(centerX: Float) { this[0] = centerX }
fun SizeAndCenter.setCenterY(centerY: Float) { this[1] = centerY }
fun SizeAndCenter.setWidth(width: Float) { this[2] = width }
fun SizeAndCenter.setHeight(height: Float) { this[3] = height }

/**
 * Convert [SizeAndCenter] (centerX, centerY, w, h) to [RectForm] (left, top, right, bottom)
 */
fun SizeAndCenter.toRectForm() {
    val left = centerX() - width() / 2
    val top = centerY() - height() / 2
    val right = centerX() + width() / 2
    val bottom = centerY() + height() / 2

    setLeft(left)
    setTop(top)
    setRight(right)
    setBottom(bottom)
}

/**
 * Clamp all values in the array to the specified [minimum] and [maximum].
 */
fun SizeAndCenter.clampAll(minimum: Float, maximum: Float) {
    setCenterX(clamp(centerX(), minimum, maximum))
    setCenterY(clamp(centerY(), minimum, maximum))
    setWidth(clamp(width(), minimum, maximum))
    setHeight(clamp(height(), minimum, maximum))
}

/**
 * Convert a regressional location result of SSD into a []SizeAndCenter].
 */
fun SizeAndCenter.adjustLocation(
    prior: SizeAndCenter,
    centerVariance: Float,
    sizeVariance: Float
) {
    setCenterX(centerX() * centerVariance * prior.width() + prior.centerX())
    setCenterY(centerY() * centerVariance * prior.height() + prior.centerY())
    setWidth(exp(width() * sizeVariance) * prior.width())
    setHeight(exp(height() * sizeVariance) * prior.height())
}

/**
 * Convert regressional location results of SSD into [SizeAndCenter] arrays.
 */
fun Array<SizeAndCenter>.adjustLocations(
    priors: Array<SizeAndCenter>,
    centerVariance: Float,
    sizeVariance: Float
) {
    for (i in this.indices) {
        this[i].adjustLocation(priors[i], centerVariance, sizeVariance)
    }
}
