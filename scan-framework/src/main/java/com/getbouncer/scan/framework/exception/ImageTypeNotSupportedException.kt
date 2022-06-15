package com.getbouncer.scan.framework.exception

import java.lang.Exception

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
class ImageTypeNotSupportedException(val imageType: Int) : Exception()
