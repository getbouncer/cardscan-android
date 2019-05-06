/* Copyright 2018 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package com.getbouncer.cardscan;

import android.app.Activity;
import java.io.IOException;

/** This classifier works with the float MobileNet model. */
public class FindFourModel extends ImageClassifier {

    final int rows = 34;
    final int cols = 51;
    final CGSize boxSize = new CGSize(80, 36);
    final CGSize cardSize = new CGSize(480, 302);
    private final int classes = 3;
    private final int digitClass = 1;
    private final int expiryClass = 2;

    /**
     * An array to hold inference results, to be feed into Tensorflow Lite as outputs. This isn't part
     * of the super class, because we need a primitive array here.
     */
    private float[][][][] labelProbArray = null;

    /**
     * Initializes an {@code ImageClassifierFloatMobileNet}.
     *
     * @param activity
     */
    FindFourModel(Activity activity) throws IOException {
        super(activity);
        labelProbArray = new float[1][rows][cols][classes];
    }

    boolean hasDigits(int row, int col) {
        return this.digitConfidence(row, col) >= 0.5;
    }

    boolean hasExpiry(int row, int col) {
        return this.expiryConfidence(row, col) >= 0.5;
    }

    float digitConfidence(int row, int col) {
        return labelProbArray[0][row][col][digitClass];
    }
    float expiryConfidence(int row, int col) {
        return labelProbArray[0][row][col][expiryClass];
    }

    @Override
    protected int getModelResource() {
        // you can download this file from
        // see build.gradle for where to obtain this file. It should be auto
        // downloaded into assets.
        return R.raw.findfour;
    }

    @Override
    protected int getImageSizeX() {
        return 480;
    }

    @Override
    protected int getImageSizeY() {
        return 302;
    }

    @Override
    protected int getNumBytesPerChannel() {
        return 4; // Float.SIZE / Byte.SIZE;
    }

    @Override
    protected void addPixelValue(int pixelValue) {
        imgData.putFloat(((pixelValue >> 16) & 0xFF) / 255.f);
        imgData.putFloat(((pixelValue >> 8) & 0xFF) / 255.f);
        imgData.putFloat((pixelValue & 0xFF) / 255.f);
    }

    @Override
    protected void runInference() {
        tflite.run(imgData, labelProbArray);
    }
}
