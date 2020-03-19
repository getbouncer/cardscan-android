package com.getbouncer.cardscan;

import android.graphics.Bitmap;

import com.getbouncer.cardscan.base.TestingImageReaderInternal;

import org.jetbrains.annotations.NotNull;

class TestingImageBridge implements TestingImageReaderInternal {
    private TestingImageReader testingImageReader;

    TestingImageBridge(@NotNull TestingImageReader testingImageReader) {
        this.testingImageReader = testingImageReader;
    }

    @Override
    public Bitmap nextImage() {
        return this.testingImageReader.nextImage();
    }
}
