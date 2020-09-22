package com.getbouncer.scan.ui.util

import android.content.Context
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Handler
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.ui.R
import kotlin.math.roundToInt

/**
 * Determine if a view is visible.
 */
fun View.isVisible() = this.visibility == View.VISIBLE

/**
 * Set a view's visibility.
 */
fun View.setVisible(visible: Boolean) {
    this.visibility = if (visible) View.VISIBLE else View.GONE
}

/**
 * Make a view visible.
 */
fun View.show() = setVisible(true)

/**
 * Make a view invisible.
 */
fun View.hide() = setVisible(false)

/**
 * Fade in a view.
 */
fun View.fadeIn(duration: Duration? = null) {
    val animation = AnimationUtils.loadAnimation(this.context, R.anim.bouncer_fade_in)
    if (!isVisible()) {
        if (duration != null) {
            animation.duration = duration.inMilliseconds.toLong()
        }
        startAnimation(animation)
        show()
    }
}

/**
 * Fade out a view.
 */
fun View.fadeOut(duration: Duration? = null) {
    val animation = AnimationUtils.loadAnimation(this.context, R.anim.bouncer_fade_out)
    if (isVisible()) {
        if (duration != null) {
            animation.duration = duration.inMilliseconds.toLong()
        }
        startAnimation(animation)
        Handler().postDelayed({ hide() }, 400)
    }
}

/**
 * Get a [ColorInt] from a [ColorRes].
 */
@ColorInt
fun Context.getColorByRes(@ColorRes colorRes: Int) = ContextCompat.getColor(this, colorRes)

/**
 * Get a [Drawable] from a [DrawableRes]
 */
fun Context.getDrawableByRes(@DrawableRes drawableRes: Int) = ContextCompat.getDrawable(this, drawableRes)

/**
 * Set the image of an [ImageView] using a [DrawableRes].
 */
fun ImageView.setDrawable(@DrawableRes drawableRes: Int) {
    this.setImageDrawable(this.context.getDrawableByRes(drawableRes))
}

/**
 * Set the image of an [ImageView] using a [DrawableRes] and start the animation.
 */
fun ImageView.setAnimated(@DrawableRes drawableRes: Int) {
    val d = this.context.getDrawableByRes(drawableRes)
    setImageDrawable(d)
    if (d is Animatable) {
        d.start()
    }
}

/**
 * Get a rect from a view.
 */
fun View.asRect() = Rect(left, top, right, bottom)

/**
 * Convert an int in DP to pixels.
 */
fun Context.dpToPixels(dp: Int) = (dp * resources.displayMetrics.density).roundToInt()
