package com.getbouncer.cardscan;

import android.graphics.Bitmap;

public interface TestingImageReader {
    /**
     * Produces images that ScanActivity uses for testing.
     *
     * This method should produce Bitmap.Config.ARGB_8888 formatted Bitmap images. The image should
     * be of a card with an aspect ration close to or exactly at w:h -> 480:302. The ScanActivity
     * will run this image through the entire pipeline, including error correction. ScanActivity
     * will show a preview of whatever it captures from the camera, but will use a small debugging
     * view at the bottom left of the screen where you can see the images that you feed it.
     *
     * @return the next image that ScanActivity processes. If you return null, ScanActivity will
     * run a blank image through instead.
     */
    Bitmap nextImage();
}
