package com.getbouncer.cardscan;

import android.graphics.Bitmap;

import java.util.Calendar;

public class Expiry {
    public String string;
    public int month;
    public int year;

    public static Expiry from(RecognizedDigitsModel model, Bitmap image, CGRect box) {
        RecognizedDigits digits = RecognizedDigits.from(model, image, box);
        String string = digits.stringResult();

        if (string.length() != 4) {
            return null;
        }

        String monthString = string.substring(0, 2);
        String yearString = string.substring(2);

        try {
            int month = Integer.parseInt(monthString);
            int year = Integer.parseInt(yearString);

            if (month <= 0 || month > 12) {
                return null;
            }

            Calendar now = Calendar.getInstance();
            int currentYear = now.get(Calendar.YEAR);
            int currentMonth = now.get(Calendar.MONTH) + 1;
            int fullYear = 2000 + year;

            if (fullYear < currentYear || fullYear >= (currentYear + 10)) {
                return null;
            }

            if (fullYear == currentYear && month < currentMonth) {
                return null;
            }

            Expiry expiry = new Expiry();
            expiry.month = month;
            expiry.year = fullYear;
            expiry.string = string;

            return expiry;
        } catch (NumberFormatException nfe) {
            return null;
        }
    }
}
