package com.getbouncer.scan.framework.ml.ssd

import android.graphics.RectF
import com.getbouncer.scan.framework.util.clamp

/**
 * An array of four floats, which denote a rectangle of the following values:
 * [0] = left percent
 * [1] = top percent
 * [2] = right percent
 * [3] = bottom percent
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
typealias RectForm = FloatArray

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
const val RECT_FORM_SIZE = 4

/**
 * Create a new [RectForm].
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun rectForm(left: Float, top: Float, right: Float, bottom: Float) =
    RectForm(RECT_FORM_SIZE).apply {
        setLeft(left)
        setTop(top)
        setRight(right)
        setBottom(bottom)
    }

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.left() = this[0]
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.top() = this[1]
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.right() = this[2]
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.bottom() = this[3]

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.setLeft(left: Float) { this[0] = left }
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.setTop(top: Float) { this[1] = top }
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.setRight(right: Float) { this[2] = right }
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.setBottom(bottom: Float) { this[3] = bottom }

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.calcWidth() = right() - left()
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.calcHeight() = bottom() - top()

/**
 * Convert this [RectForm] to a [RectF].
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.toRectF() = RectF(left(), top(), right(), bottom())

/**
 * Calculate the area of a rectangle while clamping the width and height between 0 and 1000.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.areaClamped() = clamp(calcWidth(), 0F, 1000F) * clamp(calcHeight(), 0F, 1000F)

/**
 * Create a rectangle of the overlap of this rectangle and another. Note that if the two rectangles
 * do not overlap, this can create a negative area rectangle.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun RectForm.overlapWith(other: RectForm) =
    rectForm(
        kotlin.math.max(this.left(), other.left()),
        kotlin.math.max(this.top(), other.top()),
        kotlin.math.min(this.right(), other.right()),
        kotlin.math.min(this.bottom(), other.bottom())
    )
