package com.getbouncer.cardscan;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;

import com.getbouncer.cardscan.base.TestingImageReaderInternal;

class TestingImageBridge implements TestingImageReaderInternal {
    private TestingImageReader testingImageReader;

    TestingImageBridge(@NonNull TestingImageReader testingImageReader) {
        this.testingImageReader = testingImageReader;
    }

    @Override
    public Bitmap nextImage() {
        return this.testingImageReader.nextImage();
    }
}
