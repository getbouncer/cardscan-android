package com.getbouncer.cardscan.base;

import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;
import org.json.JSONException;
import org.json.JSONObject;

public class ScanStats {
    private String flowName;
    private boolean mIsCameraPermissionGranted;
    private long endTimeMs;
    private long startTimeMs;
    private int scans;
    private boolean success;
    private long panFirstDetectedAtMs = -1;
    private int numOCRFramesProcessed = 0;
    private int numObjFramesProcessed = 0;

    ScanStats(final boolean isCameraPermissionGranted, @NotNull final String flowName) {
        startTimeMs = SystemClock.uptimeMillis();
        scans = 0;
        mIsCameraPermissionGranted = isCameraPermissionGranted;
        this.flowName = flowName;
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
    }

    public void processNumber() {
        numOCRFramesProcessed += 1;
    }

    public void processObjectDetection() {
        numObjFramesProcessed += 1;
    }

    @NonNull
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
            object.put("permission_granted", mIsCameraPermissionGranted);
            object.put("flow_name", flowName);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return object;
    }

    // from https://stackoverflow.com/a/27836910/947883
    @NonNull
    private static String getDeviceName() {
        String manufacturer = Build.MANUFACTURER;
        String model = Build.MODEL;
        if (model.startsWith(manufacturer)) {
            return capitalize(model);
        }
        return capitalize(manufacturer) + " " + model;
    }

    @NonNull
    private static String capitalize(String str) {
        if (TextUtils.isEmpty(str)) {
            return "";
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
