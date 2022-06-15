package com.getbouncer.scan.payment.card

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class PaymentCard(
    val pan: String?,
    val expiry: PaymentCardExpiry?,
    val issuer: CardIssuer?,
    val cvc: String?,
    val legalName: String?
) : Parcelable

@Parcelize
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class PaymentCardExpiry(val day: String?, val month: String, val year: String) : Parcelable
