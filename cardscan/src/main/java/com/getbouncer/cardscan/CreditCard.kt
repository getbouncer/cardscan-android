package com.getbouncer.cardscan

import android.os.Parcel
import android.os.Parcelable
import com.getbouncer.cardscan.base.CreditCardUtils
import com.getbouncer.cardscan.base.CardNetwork

class CreditCard : Parcelable {
    val number: String
    val network: CardNetwork
    val expiryMonth: String?
    val expiryYear: String?

    internal constructor(number: String, expiryMonth: String?, expiryYear: String?) {
        this.number = number
        this.expiryMonth = expiryMonth
        this.expiryYear = expiryYear
        this.network = CreditCardUtils.determineCardNetwork(number)
    }

    fun last4(): String = this.number.substring(this.number.length - 4)

    fun expiryForDisplay(): String? =
        CreditCardUtils.formatExpirationForDisplay(this.expiryMonth, this.expiryYear)

    fun numberForDisplay(): String = CreditCardUtils.formatNumberForDisplay(number)

    private constructor(parcel: Parcel) {
        val number = parcel.readString()
        this.expiryMonth = parcel.readString()
        this.expiryYear = parcel.readString()
        val network = parcel.readSerializable() as CardNetwork?

        if (number == null) {
            // this should never happen, but makes the compiler happy
            this.number = ""
        } else {
            this.number = number
        }

        if (network == null) {
            this.network = CardNetwork.UNKNOWN
        } else {
            this.network = network
        }
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, i: Int) {
        parcel.writeString(number)
        parcel.writeString(expiryMonth)
        parcel.writeString(expiryYear)
        parcel.writeSerializable(network)
    }

    companion object CREATOR : Parcelable.Creator<CreditCard> {
        override fun createFromParcel(parcel: Parcel): CreditCard = CreditCard(parcel)
        override fun newArray(size: Int): Array<CreditCard?> = arrayOfNulls(size)
    }
}