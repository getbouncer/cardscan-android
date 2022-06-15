package com.getbouncer.scan.payment.ml.ssd

import android.graphics.RectF
import androidx.annotation.RestrictTo

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class DetectionBox(

    /**
     * The rectangle percentage of the original image.
     */
    val rect: RectF,

    /**
     * Confidence value that the label applies to the rectangle.
     */
    val confidence: Float,

    /**
     * The label for this box.
     */
    val label: Int
)
