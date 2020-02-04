package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

import com.getbouncer.cardscan.base.UXModelResult;
import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.util.List;

public interface OnUXModelListener {

    void onUXModelPrediction(final Bitmap bitmap, List<DetectedSSDBox> boxes,
                             final String number, final boolean isNumberValidPan, final Expiry expiry,
                             final List<DetectedBox> digitBoxes, final DetectedBox expiryBox, UXModelResult uxModelResult,
                             int imageWidth, int imageHeight, final Bitmap fullScreenBitmap, final Bitmap originalBitmap);

    void onObjectFatalError();
}
