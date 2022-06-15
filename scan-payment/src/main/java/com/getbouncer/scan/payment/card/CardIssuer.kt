package com.getbouncer.scan.payment.card

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * A list of supported card issuers.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
sealed class CardIssuer(open val displayName: String) : Parcelable {
    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object AmericanExpress : CardIssuer("American Express")

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    data class Custom(override val displayName: String) : CardIssuer(displayName)

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object DinersClub : CardIssuer("Diners Club")

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object Discover : CardIssuer("Discover")

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object JCB : CardIssuer("JCB")

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object MasterCard : CardIssuer("MasterCard")

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object UnionPay : CardIssuer("UnionPay")

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object Unknown : CardIssuer("Unknown")

    @Parcelize
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    object Visa : CardIssuer("Visa")
}

/**
 * Format the card network as a human readable format.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun formatIssuer(issuer: CardIssuer): String = issuer.displayName
