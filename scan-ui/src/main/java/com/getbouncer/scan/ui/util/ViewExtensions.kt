package com.getbouncer.scan.ui.util

import android.content.Context
import android.graphics.drawable.Animatable
import android.os.Build
import android.os.Handler
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import com.getbouncer.scan.framework.time.Duration
import com.getbouncer.scan.ui.R

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
 * Fade in a view.
 */
fun Context.fadeIn(view: View, duration: Duration? = null) {
    val animation = AnimationUtils.loadAnimation(this, R.anim.bouncer_fade_in)
    if (!view.isVisible()) {
        if (duration != null) {
            animation.duration = duration.inMilliseconds.toLong()
        }
        view.startAnimation(animation)
        view.setVisible(true)
    }
}

fun Context.fadeOut(view: View) {
    val animation = AnimationUtils.loadAnimation(this, R.anim.bouncer_fade_out)
    if (view.isVisible()) {
        view.startAnimation(animation)
        Handler().postDelayed({ view.visibility = View.INVISIBLE }, 400)
    }
}

fun Context.setAnimated(imageView: ImageView, @DrawableRes drawable: Int) {
    val d = getDrawable(drawable)
    imageView.setImageDrawable(d)
    if (d is Animatable) {
        d.start()
    }
}

@ColorInt
fun Context.getColorByRes(@ColorRes colorRes: Int) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    resources.getColor(colorRes, theme)
} else {
    @Suppress("deprecation")
    resources.getColor(colorRes)
}
