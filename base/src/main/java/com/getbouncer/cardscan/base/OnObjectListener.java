package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

interface OnObjectListener {
    void onPrediction(final Bitmap bitmap /* TBD */);
    void onObjectFatalError();
}
