package com.getbouncer.cardscan.base;

import androidx.test.espresso.idling.CountingIdlingResource;

public class IdleResourceManager {
    static CountingIdlingResource scanningIdleResource = null;

    public static CountingIdlingResource getScanningIdleResource() {
        if (scanningIdleResource == null) {
            scanningIdleResource = new CountingIdlingResource("CardScanning");
        }

        return scanningIdleResource;
    }

}
