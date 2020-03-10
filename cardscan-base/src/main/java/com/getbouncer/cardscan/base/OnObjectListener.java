package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.util.List;

interface OnObjectListener {
    void onPrediction(
            @NonNull final Bitmap ocrBitmap,
            @Nullable List<DetectedSSDBox> boxes,
            int imageWidth,
            int imageHeight,
            @Nullable final Bitmap screenDetectionBitmap
    );

    void onObjectFatalError();
}
