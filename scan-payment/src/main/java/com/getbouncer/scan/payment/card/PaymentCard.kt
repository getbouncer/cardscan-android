package com.getbouncer.scan.payment.card

data class PaymentCard(
    val pan: String?,
    val expiry: PaymentCardExpiry?,
    val issuer: CardIssuer?,
    val cvc: String?,
    val legalName: String?
)

data class PaymentCardExpiry(val day: String?, val month: String, val year: String)
