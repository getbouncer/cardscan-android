package com.getbouncer.cardscan.base;

import android.graphics.RectF;

import androidx.annotation.NonNull;

class CGRect {
    final float x;
    final float y;
    final float width;
    final float height;
    CGRect(float x, float y, float width, float height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @NonNull
    RectF RectF() {
        return new RectF(x, y, x+width, y+height);
    }
}
