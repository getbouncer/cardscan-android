package com.getbouncer.cardscan.ui.analyzer

data class PaymentCardOcrState(
    val runOcr: Boolean,
    val runNameExtraction: Boolean,
    val runExpiryExtraction: Boolean
)
