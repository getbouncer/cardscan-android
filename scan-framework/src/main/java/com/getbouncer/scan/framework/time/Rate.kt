package com.getbouncer.scan.framework.time

/**
 * A rate of execution.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class Rate(val amount: Long, val duration: Duration) : Comparable<Rate> {
    override fun compareTo(other: Rate): Int {
        return (other.duration / other.amount).compareTo(duration / amount)
    }
}
