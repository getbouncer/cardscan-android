package com.getbouncer.cardscan;

import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class CreditCard implements Parcelable {
    @NonNull public final String number;
    @NonNull public final Network network;
    @Nullable public final String expiryMonth;
    @Nullable public final String expiryYear;

    public enum Network {VISA, MASTERCARD, AMEX, DISCOVER, UNKNOWN};

    CreditCard(@NonNull String number, @Nullable String expiryMonth,
                      @Nullable String expiryYear) {
        this.number = number;
        this.expiryMonth = expiryMonth;
        this.expiryYear = expiryYear;

        if (CreditCardUtils.isVisa(number)) {
            this.network = Network.VISA;
        } else if (CreditCardUtils.isAmex(number)) {
            this.network = Network.AMEX;
        } else if (CreditCardUtils.isDiscover(number)) {
            this.network = Network.DISCOVER;
        } else if (CreditCardUtils.isMastercard(number)) {
            this.network = Network.MASTERCARD;
        } else {
            this.network = Network.UNKNOWN;
        }

    }

    private CreditCard(Parcel in) {
        String number = in.readString();
        this.expiryMonth = in.readString();
        this.expiryYear = in.readString();
        Network network = (Network) in.readSerializable();

        if (number == null) {
            // this should never happen, but makes the compiler happy
            this.number = "";
        } else {
            this.number = number;
        }

        if (network == null) {
            this.network = Network.UNKNOWN;
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
