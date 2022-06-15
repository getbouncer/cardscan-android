package com.getbouncer.scan.payment

import androidx.annotation.Keep

@Keep
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class FrameDetails(
    val panSideConfidence: Float,
    val noPanSideConfidence: Float,
    val noCardConfidence: Float,
    val hasPan: Boolean,
)
