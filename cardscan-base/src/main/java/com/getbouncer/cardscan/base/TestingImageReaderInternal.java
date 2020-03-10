package com.getbouncer.cardscan.base;

import android.graphics.Bitmap;

import androidx.annotation.Nullable;

public interface TestingImageReaderInternal {
    @Nullable
    Bitmap nextImage();
}
