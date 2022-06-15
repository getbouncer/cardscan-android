package com.getbouncer.scan.framework

/**
 * An image with a stat tracker.
 */
@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
data class TrackedImage<ImageType>(
    val image: ImageType,
    val tracker: StatTracker,
)
