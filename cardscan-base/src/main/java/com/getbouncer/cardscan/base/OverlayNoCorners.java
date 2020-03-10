package com.getbouncer.cardscan.base;

import android.content.Context;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class OverlayNoCorners extends Overlay {
    public OverlayNoCorners(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        this.drawCorners = false;
    }
}
