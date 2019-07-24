package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.util.List;

interface OnScanListener {
    void onPrediction(final String number, final Expiry expiry, final Bitmap bitmap,
                      final List<DetectedBox> digitBoxes, final DetectedBox expiryBox, List<DetectedSSDBox> objectBoxes);
    void onFatalError();
}
