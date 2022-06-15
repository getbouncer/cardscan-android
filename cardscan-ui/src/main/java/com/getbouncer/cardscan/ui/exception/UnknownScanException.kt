package com.getbouncer.cardscan.ui.exception

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
class UnknownScanException(message: String? = null) : Exception(message)
