package com.getbouncer.cardscan.base;

import android.hardware.Camera;
import android.support.annotation.Nullable;

interface OnCameraOpenListener {
    void onCameraOpen(@Nullable Camera camera);
}
