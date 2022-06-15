package com.getbouncer.scan.framework.time

import kotlin.math.roundToLong

/**
 * Allow delaying for a specified duration
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
suspend fun delay(duration: Duration) =
    kotlinx.coroutines.delay(duration.inMilliseconds.roundToLong())
