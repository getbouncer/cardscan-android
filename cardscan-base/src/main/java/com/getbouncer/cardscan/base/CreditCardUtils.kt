/*
The MIT License

Copyright (c) 2011- Stripe, Inc. (https://stripe.com)

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
*/

package com.getbouncer.cardscan.base

import androidx.annotation.DrawableRes
import java.io.Serializable
import java.util.*

enum class CardNetwork(val displayName: String) : Serializable {
    AMEX(displayName = "American Express"),
    DISCOVER(displayName = "Discover"),
    JCB(displayName = "JCB"),
    DINERS_CLUB(displayName = "Diners Club"),
    VISA(displayName = "Visa"),
    MASTERCARD(displayName = "MasterCard"),
    UNIONPAY(displayName = "UnionPay"),
    UNKNOWN(displayName = "Unknown")
}

object CreditCardUtils {
    const val LENGTH_COMMON_CARD = 16
    const val LENGTH_AMEX = 15

    private const val CVC_LENGTH_AMEX: Int = 4
    private const val CVC_LENGTH_COMMON: Int = 3

    /**
     * Based on [Issuer identification number table](http://en.wikipedia.org/wiki/Bank_card_number#Issuer_identification_number_.28IIN.29)
     */
    private val PREFIXES_AMEX: Array<String> = arrayOf("34", "37")
    private val PREFIXES_DISCOVER: Array<String> = arrayOf("60", "64", "65")
    private val PREFIXES_JCB: Array<String> = arrayOf("35")
    private val PREFIXES_DINERS_CLUB: Array<String> = arrayOf(
            "300", "301", "302", "303", "304", "305", "309", "36", "38", "39"
    )
    private val PREFIXES_VISA: Array<String> = arrayOf("4")
    private val PREFIXES_MASTERCARD: Array<String> = arrayOf(
            "2221", "2222", "2223", "2224", "2225", "2226", "2227", "2228", "2229", "223", "224",
            "225", "226", "227", "228", "229", "23", "24", "25", "26", "270", "271", "2720",
            "50", "51", "52", "53", "54", "55", "67"
    )
    private val PREFIXES_UNIONPAY: Array<String> = arrayOf("62")

    private const val LENGTH_DINERS_CLUB = 14

    private val NETWORK_RESOURCE_MAP = mapOf(
            CardNetwork.AMEX to R.drawable.bouncer_card_amex,
            CardNetwork.DINERS_CLUB to R.drawable.bouncer_card_diners,
            CardNetwork.DISCOVER to R.drawable.bouncer_card_discover,
            CardNetwork.JCB to R.drawable.bouncer_card_jcb,
            CardNetwork.MASTERCARD to R.drawable.bouncer_card_mastercard,
            CardNetwork.VISA to R.drawable.bouncer_card_visa,
            CardNetwork.UNIONPAY to R.drawable.bouncer_card_unionpay,
            CardNetwork.UNKNOWN to R.drawable.bouncer_card_unknown
    )

    /**
     * Returns a [CardNetwork] corresponding to a partial card number, or [CardNetwork.UNKNOWN] if
     * the card network can't be determined from the input value.
     *
     * @param cardNumber a credit card number or partial card number
     * @return the [CardNetwork] corresponding to that number,
     * or [CardNetwork.UNKNOWN] if it can't be determined
     */
    @JvmStatic
    fun determineCardNetwork(cardNumber: String?): CardNetwork =
            determineCardNetwork(cardNumber, true)

    /**
     * Checks the input string to see whether or not it is a valid card number, possibly with
     * groupings separated by spaces or hyphens.
     *
     * @param cardNumber a String that may or may not represent a valid card number
     * @return `true` if and only if the input value is a valid card number
     */
    @JvmStatic
    fun isValidCardNumber(cardNumber: String?): Boolean {
        val normalizedNumber = removeSpacesAndHyphens(cardNumber)
        return isValidLuhnNumber(normalizedNumber) && isValidCardLength(normalizedNumber)
    }

    /**
     * Checks the input string to see whether or not it is a valid Luhn number. This is based on an
     * example from wikipedia: https://en.wikipedia.org/wiki/Luhn_algorithm#Java
     *
     * @param cardNumber a String that may or may not represent a valid Luhn number
     * @return `true` if and only if the input value is a valid Luhn number
     */
    private fun isValidLuhnNumber(cardNumber: String?): Boolean {
        if (cardNumber.isNullOrEmpty()) {
            return false
        }

        var isOdd = true
        var sum = 0

        for (index in cardNumber.length - 1 downTo 0) {
            val c = cardNumber[index]
            if (!Character.isDigit(c)) {
                return false
            }

            var digitInteger = Character.getNumericValue(c)
            isOdd = !isOdd

            if (isOdd) {
                digitInteger *= 2
            }

            if (digitInteger > 9) {
                digitInteger -= 9
            }

            sum += digitInteger
        }

        return sum % 10 == 0
    }

    /**
     * Checks the Bank Identification Number to see if it belongs to a known network.
     *
     * @param bin a String that may or may not represent a valid BIN
     * @return `true` if and only if the input valid is a valid BIN
     */
    @JvmStatic
    fun isValidBin(bin: String?): Boolean = determineCardNetwork(bin) != CardNetwork.UNKNOWN

    /**
     * Checks to see whether the input number is of the correct length, after determining its
     * network. This function does not perform a Luhn check.
     *
     * @param cardNumber the card number with no spaces or dashes
     * @return `true` if the card number is of known type and the correct length
     */
    private fun isValidCardLength(cardNumber: String?): Boolean =
        cardNumber != null && isValidCardLength(cardNumber,
                determineCardNetwork(cardNumber, false))

    /**
     * Checks to see whether the input number is of the correct length, given the assumed network of
     * the card. This function does not perform a Luhn check.
     *
     * @param cardNumber the card number with no spaces or dashes
     * @param cardNetwork a [CardNetwork] used to get the correct size
     * @return `true` if the card number is the correct length for the assumed network
     */
    private fun isValidCardLength(cardNumber: String?, cardNetwork: CardNetwork): Boolean {
        if (cardNumber == null || CardNetwork.UNKNOWN == cardNetwork) {
            return false
        }

        val length = cardNumber.length
        return when (cardNetwork) {
            CardNetwork.AMEX -> length == LENGTH_AMEX
            CardNetwork.DINERS_CLUB -> length == LENGTH_DINERS_CLUB
            else -> length == LENGTH_COMMON_CARD
        }
    }

    private fun determineCardNetwork(cardNumber: String?, shouldNormalize: Boolean): CardNetwork {
        if (cardNumber.isNullOrBlank()) {
            return CardNetwork.UNKNOWN
        }

        val normalizedCardNumber =
            if (shouldNormalize) {
                removeSpacesAndHyphens(cardNumber)
            } else {
                cardNumber
            }

        return when {
            hasAnyPrefix(normalizedCardNumber, *PREFIXES_AMEX) -> CardNetwork.AMEX
            hasAnyPrefix(normalizedCardNumber, *PREFIXES_DISCOVER) -> CardNetwork.DISCOVER
            hasAnyPrefix(normalizedCardNumber, *PREFIXES_JCB) -> CardNetwork.JCB
            hasAnyPrefix(normalizedCardNumber, *PREFIXES_DINERS_CLUB) -> CardNetwork.DINERS_CLUB
            hasAnyPrefix(normalizedCardNumber, *PREFIXES_VISA) -> CardNetwork.VISA
            hasAnyPrefix(normalizedCardNumber, *PREFIXES_MASTERCARD) -> CardNetwork.MASTERCARD
            hasAnyPrefix(normalizedCardNumber, *PREFIXES_UNIONPAY) -> CardNetwork.UNIONPAY
            else -> CardNetwork.UNKNOWN
        }
    }

    /**
     * Checks whether or not the [cvc] is valid.
     *
     * @param cvc: The CVC to validate
     * @return `true` if valid, `false` otherwise
     */
    @JvmStatic
    fun isValidCVC(cvc: String?, network: CardNetwork?): Boolean {
        if (cvc.isNullOrEmpty()) {
            return false
        }
        val cvcValue = cvc.trim()
        val validLength =
                network == null && cvcValue.length >= 3 && cvcValue.length <= 4 ||
                        CardNetwork.AMEX == network && cvcValue.length == CVC_LENGTH_AMEX ||
                        cvcValue.length == CVC_LENGTH_COMMON

        return isWholePositiveNumber(cvcValue) && validLength
    }

    /**
     * Checks whether or not the [expMonth] and [expYear] fields represent a valid
     * expiration date.
     *
     * @return `true` if valid, `false` otherwise
     */
    @JvmStatic
    fun isValidExpirationDate(expMonth: String?, expYear: String?): Boolean =
        isValidExpirationDate(expMonth, expYear, Calendar.getInstance())

    private fun isValidExpirationDate(
        expMonth: String?,
        expYear: String?,
        now: Calendar
    ): Boolean {
        val expirationMonth = asInteger(expMonth)
        val expirationYear = asInteger(expYear)
        if (expirationMonth == null || !isValidExpMonth(expirationMonth)) {
            return false
        }

        return if (expirationYear == null || !isValidExpYear(expirationYear, now)) {
            false
        } else {
            !hasMonthPassed(expirationYear, expirationMonth, now)
        }
    }

    @JvmStatic
    @DrawableRes
    fun getNetworkIcon(network: CardNetwork?): Int =
        NETWORK_RESOURCE_MAP[network] ?: R.drawable.bouncer_card_unknown

    @JvmStatic
    fun formatNumberForDisplay(number: String): String {
        if (number.length == LENGTH_COMMON_CARD) {
            return formatCommonForDisplay(number)
        } else if (number.length == LENGTH_AMEX) {
            return formatAmexForDisplay(number)
        }

        return number
    }

    @JvmStatic
    fun formatNetworkForDisplay(network: CardNetwork): String = network.displayName

    @JvmStatic
    fun formatExpirationForDisplay(expMonth: String?, expYear: String?): String? {
        if (expMonth == null || expYear == null) {
            return null
        }

        val month = if (expMonth.length == 1) "0$expMonth" else expMonth
        val year = if (expYear.length == 4) expYear.substring(2) else expYear

        return "$month/$year"
    }

    private fun formatCommonForDisplay(number: String): String {
        val result = StringBuilder()
        for (idx in number.indices) {
            if (idx == 4 || idx == 8 || idx == 12) {
                result.append(" ")
            }
            result.append(number[idx])
        }

        return result.toString()
    }

    private fun formatAmexForDisplay(number: String): String {
        val result = StringBuilder()
        for (idx in number.indices) {
            if (idx == 4 || idx == 10) {
                result.append(" ")
            }
            result.append(number[idx])
        }

        return result.toString()
    }

    /**
     * Checks whether or not the [expMonth] field is valid.
     *
     * @return `true` if valid, `false` otherwise.
     */
    private fun isValidExpMonth(expMonth: Int?): Boolean = expMonth?.let { it in 1..12 } == true

    /**
     * Checks whether or not the [expYear] field is valid.
     *
     * @return `true` if valid, `false` otherwise.
     */
    private fun isValidExpYear(expYear: Int?, now: Calendar): Boolean =
        expYear?.let { !hasYearPassed(it, now) } == true

    /**
     * Determines whether the input year-month pair has passed.
     *
     * @param year the input year, as a two or four-digit integer
     * @param month the input month
     * @param now the current time
     * @return `true` if the input time has passed the specified current time,
     * `false` otherwise.
     */
    private fun hasMonthPassed(year: Int, month: Int, now: Calendar): Boolean =
        if (hasYearPassed(year, now)) {
            true
        } else {
            // Expires at end of specified month, Calendar month starts at 0
            normalizeYear(year, now) == now.get(Calendar.YEAR) &&
                    month < now.get(Calendar.MONTH) + 1
        }

    /**
     * Determines whether or not the input year has already passed.
     *
     * @param year the input year, as a two or four-digit integer
     * @param now, the current time
     * @return `true` if the input year has passed the year of the specified current time
     * `false` otherwise.
     */
    private fun hasYearPassed(year: Int, now: Calendar): Boolean =
        normalizeYear(year, now) < now.get(Calendar.YEAR)

    /**
     * Normalize years to four digits. If the year is only two digits, append the current century
     * to the beginning of the year.
     */
    private fun normalizeYear(year: Int, now: Calendar): Int =
        if (year in 0..99) {
            val currentYear = now.get(Calendar.YEAR).toString()
            val prefix = currentYear.substring(0, currentYear.length - 2)
            String.format(Locale.US, "%s%02d", prefix, year).toInt()
        } else {
            year
        }

    /**
     * Converts a card number that may have spaces between the numbers into one without any spaces.
     * Note: method does not check that all characters are digits or spaces.
     *
     * @param cardNumberWithSpaces a card number, for instance "4242 4242 4242 4242"
     * @return the input number minus any spaces, for instance "4242424242424242".
     * Returns `null` if the input was `null` or all spaces.
     */
    private fun removeSpacesAndHyphens(cardNumberWithSpaces: String?): String? =
        cardNumberWithSpaces.takeUnless { it.isNullOrBlank() }
                ?.replace("\\s|-".toRegex(), "")

    /**
     * Check to see if the input number has any of the given prefixes.
     *
     * @param number the number to test
     * @param prefixes the prefixes to test against
     * @return `true` if number begins with any of the input prefixes
     */
    private fun hasAnyPrefix(number: String?, vararg prefixes: String): Boolean =
        prefixes.any { number?.startsWith(it) == true }


    /**
     * Check to see whether the input string is a whole, positive number.
     *
     * @param value the input string to test
     * @return `true` if the input value consists entirely of integers
     */
    private fun isWholePositiveNumber(value: String?): Boolean =
        value != null && isDigitsOnly(value)

    /**
     * Returns whether the given CharSequence contains only digits.
     */
    private fun isDigitsOnly(str: CharSequence): Boolean {
        var i = 0
        while (i < str.length) {
            val cp = Character.codePointAt(str, i)
            if (!Character.isDigit(cp)) {
                return false
            }
            i += Character.charCount(cp)
        }
        return true
    }

    private fun asInteger(str: String?): Int? =
        if (str != null && isDigitsOnly(str)) str.toInt() else null
}