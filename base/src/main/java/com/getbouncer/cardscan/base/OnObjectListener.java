package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.util.List;

interface OnObjectListener {
    void onPrediction(final Bitmap bitmap, List<DetectedSSDBox> boxes);
    void onObjectFatalError();
}
