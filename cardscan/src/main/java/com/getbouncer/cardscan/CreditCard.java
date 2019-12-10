package com.getbouncer.cardscan;

import android.os.Parcel;
import android.os.Parcelable;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.getbouncer.cardscan.base.CreditCardUtils;

public class CreditCard  implements Parcelable {
    @NonNull public final String number;
    @NonNull public final CreditCard.Network network;
    @Nullable public final String expiryMonth;
    @Nullable public final String expiryYear;

    public enum Network {VISA, MASTERCARD, AMEX, DISCOVER, UNIONPAY, UNKNOWN, EGP_MEEZA};

    CreditCard(@NonNull String number, @Nullable String expiryMonth,
                          @Nullable String expiryYear) {
        this.number = number;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;

        if (CreditCardUtils.isVisa(number)) {
            this.network = CreditCard.Network.VISA;
        } else if (CreditCardUtils.isAmex(number)) {
            this.network = CreditCard.Network.AMEX;
        } else if (CreditCardUtils.isDiscover(number)) {
            this.network = CreditCard.Network.DISCOVER;
        } else if (CreditCardUtils.isMastercard(number)) {
            this.network = CreditCard.Network.MASTERCARD;
        } else if (CreditCardUtils.isUnionPay(number)) {
            this.network = Network.UNIONPAY;
        } else if (CreditCardUtils.isEgpMeeza(number)) {
            this.network = Network.EGP_MEEZA;
        } else {
            this.network = CreditCard.Network.UNKNOWN;
        }

    }

    @NonNull public String last4() {
        return this.number.substring(this.number.length() - 4);
    }

    @Nullable public String expiryForDisplay() {
        if (this.expiryMonth == null || this.expiryYear == null) {
            return null;
        }

        String month = this.expiryMonth;
        if (month.length() == 1) {
            month = "0" + month;
        }
        String year = this.expiryYear;
        if (year.length() == 4) {
            year = year.substring(2);
        }

        return month + "/" + year;
    }

    private CreditCard(Parcel in) {
        String number = in.readString();
        this.expiryMonth = in.readString();
        this.expiryYear = in.readString();
        CreditCard.Network network = (CreditCard.Network) in.readSerializable();

        if (number == null) {
            // this should never happen, but makes the compiler happy
            this.number = "";
        } else {
            this.number = number;
        }

        if (network == null) {
            this.network = CreditCard.Network.UNKNOWN;
        } else {
            this.network = network;
        }
    }

    public static final Creator<CreditCard> CREATOR = new Creator<CreditCard>() {
        @Override
        public CreditCard createFromParcel(Parcel in) {
            return new CreditCard(in);
        }

        @Override
        public CreditCard[] newArray(int size) {
            return new CreditCard[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int i) {
        parcel.writeString(number);
        parcel.writeString(expiryMonth);
        parcel.writeString(expiryYear);
        parcel.writeSerializable(network);
    }
}