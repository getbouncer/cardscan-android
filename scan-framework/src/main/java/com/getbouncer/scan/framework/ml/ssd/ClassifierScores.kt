package com.getbouncer.scan.framework.ml.ssd

import com.getbouncer.scan.framework.util.updateEach
import kotlin.math.exp

typealias ClassifierScores = FloatArray

/**
 * Compute softmax for the given row. This will replace each row value with a value normalized by
 * the sum of all the values in the row.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun ClassifierScores.softMax() {
    val rowSumExp = this.fold(0F) { acc, element -> acc + exp(element) }
    this.updateEach { exp(it) / rowSumExp }
}
