package com.getbouncer.scan.payment.card

interface RequiresMatchingCard {
    val requiredIin: String?
    val requiredLastFour: String?

    fun matchesRequiredCard(pan: String?) =
        pan != null && isValidPan(pan) && panMatches(requiredIin, requiredLastFour, pan)

    fun wrongCardDetected(pan: String) =
        isValidPan(pan) && !panMatches(requiredIin, requiredLastFour, pan)
}