package com.getbouncer.cardscan.base

import androidx.annotation.DrawableRes
import androidx.annotation.StringDef
import org.json.JSONException
import org.json.JSONObject

@Retention(AnnotationRetention.SOURCE)
@StringDef(CardBrand.AMERICAN_EXPRESS, CardBrand.DISCOVER, CardBrand.JCB,
        CardBrand.DINERS_CLUB, CardBrand.VISA, CardBrand.MASTERCARD,
        CardBrand.UNIONPAY, CardBrand.UNKNOWN)
annotation class CardBrand {
    companion object {
        const val AMERICAN_EXPRESS: String = "American Express"
        const val DISCOVER: String = "Discover"
        const val JCB: String = "JCB"
        const val DINERS_CLUB: String = "Diners Club"
        const val VISA: String = "Visa"
        const val MASTERCARD: String = "MasterCard"
        const val UNIONPAY: String = "UnionPay"
        const val UNKNOWN: String = "Unknown"
    }
}

object Card {
    const val CVC_LENGTH_AMERICAN_EXPRESS: Int = 4
    const val CVC_LENGTH_COMMON: Int = 3

    /**
     * Based on [Issuer identification number table](http://en.wikipedia.org/wiki/Bank_card_number#Issuer_identification_number_.28IIN.29)
     */
    val PREFIXES_AMERICAN_EXPRESS: Array<String> = arrayOf("34", "37")
    val PREFIXES_DISCOVER: Array<String> = arrayOf("60", "64", "65")
    val PREFIXES_JCB: Array<String> = arrayOf("35")
    val PREFIXES_DINERS_CLUB: Array<String> = arrayOf(
            "300", "301", "302", "303", "304", "305", "309", "36", "38", "39"
    )
    val PREFIXES_VISA: Array<String> = arrayOf("4")
    val PREFIXES_MASTERCARD: Array<String> = arrayOf(
            "2221", "2222", "2223", "2224", "2225", "2226", "2227", "2228", "2229", "223", "224",
            "225", "226", "227", "228", "229", "23", "24", "25", "26", "270", "271", "2720",
            "50", "51", "52", "53", "54", "55", "67"
    )
    val PREFIXES_UNIONPAY: Array<String> = arrayOf("62")

    const val MAX_LENGTH_STANDARD: Int = 16
    const val MAX_LENGTH_AMERICAN_EXPRESS: Int = 15
    const val MAX_LENGTH_DINERS_CLUB: Int = 14

    internal const val OBJECT_TYPE = "card"

    private val BRAND_RESOURCE_MAP = mapOf(
            CardBrand.AMERICAN_EXPRESS to R.drawable.stripe_ic_amex,
            CardBrand.DINERS_CLUB to R.drawable.stripe_ic_diners,
            CardBrand.DISCOVER to R.drawable.stripe_ic_discover,
            CardBrand.JCB to R.drawable.stripe_ic_jcb,
            CardBrand.MASTERCARD to R.drawable.stripe_ic_mastercard,
            CardBrand.VISA to R.drawable.stripe_ic_visa,
            CardBrand.UNIONPAY to R.drawable.stripe_ic_unionpay,
            CardBrand.UNKNOWN to R.drawable.stripe_ic_unknown
    )

    /**
     * Converts an unchecked String value to a [CardBrand] or `null`.
     *
     * @param possibleCardType a String that might match a [CardBrand] or be empty.
     * @return `null` if the input is blank, else the appropriate [CardBrand].
     */
    @JvmStatic
    @CardBrand
    fun asCardBrand(possibleCardType: String?): String? {
        if (possibleCardType.isNullOrBlank()) {
            return null
        }

        return when {
            CardBrand.AMERICAN_EXPRESS.equals(possibleCardType, ignoreCase = true) ->
                CardBrand.AMERICAN_EXPRESS
            CardBrand.MASTERCARD.equals(possibleCardType, ignoreCase = true) ->
                CardBrand.MASTERCARD
            CardBrand.DINERS_CLUB.equals(possibleCardType, ignoreCase = true) ->
                CardBrand.DINERS_CLUB
            CardBrand.DISCOVER.equals(possibleCardType, ignoreCase = true) ->
                CardBrand.DISCOVER
            CardBrand.JCB.equals(possibleCardType, ignoreCase = true) ->
                CardBrand.JCB
            CardBrand.VISA.equals(possibleCardType, ignoreCase = true) ->
                CardBrand.VISA
            CardBrand.UNIONPAY.equals(possibleCardType, ignoreCase = true) ->
                CardBrand.UNIONPAY
            else -> CardBrand.UNKNOWN
        }
    }

    @JvmStatic
    @DrawableRes
    fun getBrandIcon(brand: String?): Int {
        val brandIcon = BRAND_RESOURCE_MAP[brand]
        return brandIcon ?: R.drawable.stripe_ic_unknown
    }
}