package com.getbouncer.cardscan.base;

import androidx.annotation.NonNull;

public class Expiry {
    public String string;
    public int month;
    public int year;

    @NonNull
    public String format() {
        StringBuilder result = new StringBuilder();
        for (int idx = 0; idx < string.length(); idx++) {
            if (idx == 2) {
                result.append("/");
            }
            result.append(string.charAt(idx));
        }

        return result.toString();
    }
}
