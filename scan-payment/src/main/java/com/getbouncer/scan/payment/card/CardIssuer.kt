package com.getbouncer.scan.payment.card

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A list of supported card issuers.
 */
sealed class CardIssuer(open val displayName: String) : Parcelable {
    @Parcelize
    object AmericanExpress : CardIssuer("American Express")

    @Parcelize
    data class Custom(override val displayName: String) : CardIssuer(displayName)

    @Parcelize
    object DinersClub : CardIssuer("Diners Club")

    @Parcelize
    object Discover : CardIssuer("Discover")

    @Parcelize
    object JCB : CardIssuer("JCB")

    @Parcelize
    object MasterCard : CardIssuer("MasterCard")

    @Parcelize
    object UnionPay : CardIssuer("UnionPay")

    @Parcelize
    object Unknown : CardIssuer("Unknown")

    @Parcelize
    object Visa : CardIssuer("Visa")
}

/**
 * Format the card network as a human readable format.
 */
fun formatIssuer(issuer: CardIssuer): String = issuer.displayName
