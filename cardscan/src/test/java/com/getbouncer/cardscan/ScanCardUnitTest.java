package com.getbouncer.cardscan;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ScanCardUnitTest {
    @Test
    public void scanningErrorCorrection_isCorrect() {
        /*
        ScanActivity scanActivity = new ScanActivity();
        scanActivity.incrementNumber("4242");
        assertEquals("4242", scanActivity.getNumberResult());
        scanActivity.incrementNumber("4242");
        scanActivity.incrementNumber("4242");
        assertEquals("4242", scanActivity.getNumberResult());
        scanActivity.incrementNumber("1234");
        assertEquals("4242", scanActivity.getNumberResult());
        scanActivity.incrementNumber("1234");
        assertEquals("4242", scanActivity.getNumberResult());
        scanActivity.incrementNumber("1234");
        scanActivity.incrementNumber("1234");
        assertEquals("1234", scanActivity.getNumberResult());

        Expiry expiry1 = new Expiry();
        expiry1.string = "0120";
        expiry1.month = 1;
        expiry1.year = 2020;

        Expiry expiry2 = new Expiry();
        expiry2.string = "1121";
        expiry2.month = 11;
        expiry2.year = 2021;

        scanActivity.incrementExpiry(expiry1);
        assertEquals(expiry1, scanActivity.getExpiryResult());
        scanActivity.incrementExpiry(expiry1);
        scanActivity.incrementExpiry(expiry1);
        assertEquals(expiry1, scanActivity.getExpiryResult());
        scanActivity.incrementExpiry(expiry2);
        assertEquals(expiry1, scanActivity.getExpiryResult());
        scanActivity.incrementExpiry(expiry2);
        assertEquals(expiry1, scanActivity.getExpiryResult());
        scanActivity.incrementExpiry(expiry2);
        scanActivity.incrementExpiry(expiry2);
        assertEquals(expiry2, scanActivity.getExpiryResult());
        */
    }

    @Test
    public void expiryForDisplay_isCorrect() {
        CreditCard card = new CreditCard("4242424242424242", "5", "1975");
        String display = card.expiryForDisplay();
        assertEquals(display, "05/75");
    }
}