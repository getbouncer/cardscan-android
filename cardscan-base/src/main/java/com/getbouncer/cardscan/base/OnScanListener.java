package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

interface OnScanListener {
    void onPrediction(
            @Nullable final String number,
            @Nullable final Expiry expiry,
            @NonNull final Bitmap ocrDetectionBitmap,
            @Nullable final List<DetectedBox> digitBoxes,
            @Nullable final DetectedBox expiryBox,
            @NonNull final Bitmap objectDetectionBitmap,
            @Nullable final Bitmap screenDetectionBitmap,
            final long frameAddedTimeMs
    );
    void onFatalError();
}
