package com.getbouncer.scan.framework.exception

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
object InvalidBouncerApiKeyException : Exception()
