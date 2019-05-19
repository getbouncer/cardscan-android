package com.getbouncer.cardscan.ui;

import android.graphics.Bitmap;

import com.getbouncer.cardscan.DetectedBox;
import com.getbouncer.cardscan.Expiry;

import java.util.List;

interface OnScanListener {
    void onPrediction(final String number, final Expiry expiry, final Bitmap bitmap,
                      final List<DetectedBox> digitBoxes, final DetectedBox expiryBox);
}
