package com.getbouncer.cardscan.base;

import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

class ScanStats {
    private long endTimeMs;
    private long startTimeMs;
    private int scans;
    private boolean success;

    ScanStats() {
        startTimeMs = SystemClock.uptimeMillis();
        scans = 0;
    }

    void incrementScans() {
        scans += 1;
    }

    void setSuccess(boolean success) {
        this.success = success;
        this.endTimeMs = SystemClock.uptimeMillis();
    }

    JSONObject toJson() {
        JSONObject object = new JSONObject();
        double duration = ((double) endTimeMs - startTimeMs) / 1000.0;

        try {
            object.put("success", this.success);
            object.put("scans", this.scans);
            object.put("torch_on", false);
            object.put("duration", duration);
            object.put("model", "FindFour");
            object.put("device_type", getDeviceName());
            object.put("os", Build.VERSION.RELEASE);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object;
    }

    // from https://stackoverflow.com/a/27836910/947883
    private static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return str;
        }
        char[] arr = str.toCharArray();
        boolean capitalizeNext = true;

        StringBuilder phrase = new StringBuilder();
        for (char c : arr) {
            if (capitalizeNext && Character.isLetter(c)) {
                phrase.append(Character.toUpperCase(c));
                capitalizeNext = false;
                continue;
            } else if (Character.isWhitespace(c)) {
                capitalizeNext = true;
            }
            phrase.append(c);
        }

        return phrase.toString();
    }
}
