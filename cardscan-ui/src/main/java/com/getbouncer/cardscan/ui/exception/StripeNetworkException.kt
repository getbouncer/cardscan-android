package com.getbouncer.cardscan.ui.exception

import java.lang.Exception

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
class StripeNetworkException(message: String) : Exception(message)
