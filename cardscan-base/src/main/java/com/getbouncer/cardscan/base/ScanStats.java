package com.getbouncer.cardscan.base;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

public class ScanStats {
    private Activity parentActivity;
    private long endTimeMs;
    private long startTimeMs;
    private int scans;
    private boolean success;
    private long panFirstDetectedAtMs = -1;
    private long panLastDetectedAtMs = -1;

    ScanStats(Activity activity) {
        startTimeMs = SystemClock.uptimeMillis();
        scans = 0;
        parentActivity = activity;
    }

    void incrementScans() {
        scans += 1;
    }

    void setSuccess(boolean success) {
        this.success = success;
        this.endTimeMs = SystemClock.uptimeMillis();
    }

    void observePAN() {
        // panFirstDetectedAtMs represents, globally, when we first saw a valid card number
        if (panFirstDetectedAtMs == -1) {
            panFirstDetectedAtMs = SystemClock.uptimeMillis();
        }
        panLastDetectedAtMs = SystemClock.uptimeMillis();
    }

    public JSONObject toJson() {
        JSONObject object = new JSONObject();
        double duration = ((double) endTimeMs - startTimeMs) / 1000.0;
        long panFirstDetectedDurationMs = -1;
        if (panFirstDetectedAtMs > 0) {
            panFirstDetectedDurationMs = panFirstDetectedAtMs - startTimeMs;
        }

        try {
            object.put("success", this.success);
            object.put("scans", this.scans);
            object.put("torch_on", false);
            object.put("duration", duration);
            object.put("pan_first_detected_duration_ms", panFirstDetectedDurationMs);
            object.put("model", "FindFour");
            object.put("device_type", getDeviceName());
            object.put("sdk_version", BuildConfig.CARDSCAN_VERSION);
            object.put("os", Build.VERSION.RELEASE);
            object.put("permission_granted", ContextCompat.checkSelfPermission(
                    parentActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED);
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
