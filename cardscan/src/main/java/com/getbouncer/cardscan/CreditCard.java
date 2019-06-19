package com.getbouncer.cardscan;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.getbouncer.cardscan.base.CreditCardBase;

public class CreditCard extends CreditCardBase {
    CreditCard(@NonNull String number, @Nullable String expiryMonth,
                   @Nullable String expiryYear) {
        super(number, expiryMonth, expiryYear);
    }
}
