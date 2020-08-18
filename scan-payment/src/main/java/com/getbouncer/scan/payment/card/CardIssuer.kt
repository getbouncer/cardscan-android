package com.getbouncer.scan.payment.card

/**
 * A list of supported card issuers.
 */
sealed class CardIssuer(open val displayName: String) {
    object AmericanExpress : CardIssuer("American Express")
    object DinersClub : CardIssuer("Diners Club")
    object Discover : CardIssuer("Discover")
    object JCB : CardIssuer("JCB")
    object MasterCard : CardIssuer("MasterCard")
    object UnionPay : CardIssuer("UnionPay")
    object Unknown : CardIssuer("Unknown")
    object Visa : CardIssuer("Visa")
}

/**
 * A data class for custom card issuers
 */
data class CustomCardIssuer(override val displayName: String) : CardIssuer(displayName)

/**
 * Format the card network as a human readable format.
 */
fun formatIssuer(issuer: CardIssuer): String = issuer.displayName
