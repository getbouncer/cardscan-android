package com.getbouncer.scan.ui.util

import android.content.Context
import android.content.res.Resources.NotFoundException
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.util.TypedValue
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DimenRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import kotlin.math.roundToInt

/**
 * Determine if a view is visible.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun View.isVisible() = this.visibility == View.VISIBLE

/**
 * Set a view's visibility.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun View.setVisible(visible: Boolean) {
    this.visibility = if (visible) View.VISIBLE else View.GONE
}

/**
 * Make a view visible.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun View.show() = setVisible(true)

/**
 * Make a view invisible.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun View.hide() = setVisible(false)

/**
 * Get a [ColorInt] from a [ColorRes].
 */
@ColorInt
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun Context.getColorByRes(@ColorRes colorRes: Int) = ContextCompat.getColor(this, colorRes)

/**
 * Get a [Drawable] from a [DrawableRes]
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun Context.getDrawableByRes(@DrawableRes drawableRes: Int) = ContextCompat.getDrawable(
    this,
    drawableRes
)

/**
 * Set the image of an [ImageView] using a [DrawableRes].
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun ImageView.setDrawable(@DrawableRes drawableRes: Int) {
    this.setImageDrawable(this.context.getDrawableByRes(drawableRes))
}

/**
 * Set the image of an [ImageView] using a [DrawableRes] and start the animation.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun ImageView.startAnimation(@DrawableRes drawableRes: Int) {
    val d = this.context.getDrawableByRes(drawableRes)
    setImageDrawable(d)
    if (d is Animatable) {
        d.start()
    }
}

/**
 * Get a rect from a view.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun View.asRect() = Rect(left, top, right, bottom)

/**
 * Convert an int in DP to pixels.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun Context.dpToPixels(dp: Int) = (dp * resources.displayMetrics.density).roundToInt()

/**
 * This is copied from Resources.java for API 29 so that we can continue to support API 21.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun Context.getFloatResource(@DimenRes id: Int): Float {
    val value = TypedValue()
    resources.getValue(id, value, true)
    if (value.type == TypedValue.TYPE_FLOAT) {
        return value.float
    }
    throw NotFoundException("Resource ID #0x ${Integer.toHexString(id)} type #0x${Integer.toHexString(value.type)} is not valid")
}

/**
 * Set the size of a text field using a dimension.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun TextView.setTextSizeByRes(@DimenRes id: Int) {
    setTextSize(TypedValue.COMPLEX_UNIT_PX, this.resources.getDimension(id))
}

/**
 * Determine the center point of a view.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun View.centerPoint() = PointF(left + width / 2F, top + height / 2F)
