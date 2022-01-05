package com.getbouncer.scan.payment.card

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class PaymentCard(
    val pan: String?,
    val expiry: PaymentCardExpiry?,
    val issuer: CardIssuer?,
    val cvc: String?,
    val legalName: String?
) : Parcelable

@Parcelize
data class PaymentCardExpiry(val day: String?, val month: String, val year: String) : Parcelable
