package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.getbouncer.cardscan.base.UXModelResult;
import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.util.List;

public interface OnUXModelListener {

    void onUXModelPrediction(
            @Nullable final Bitmap bitmap,
            @Nullable List<DetectedSSDBox> boxes,
            @Nullable final String number,
            final boolean isNumberValidPan,
            @Nullable final Expiry expiry,
            @Nullable final List<DetectedBox> digitBoxes,
            @Nullable final DetectedBox expiryBox,
            @Nullable UXModelResult uxModelResult,
            int imageWidth,
            int imageHeight,
            @NonNull final Bitmap fullScreenBitmap,
            @NonNull final Bitmap originalBitmap
    );

    void onObjectFatalError();
}
