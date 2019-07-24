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

package com.getbouncer.cardscan.base;

import android.content.Context;
import android.util.Log;


import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;


class SSDDetect extends ImageClassifier {

    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 128.5f;
    private static final int CROP_SIZE = 300;
    private static final int NUM_THREADS = 4;

    private boolean isModelQuantized; // TODO later

    static final int NUM_OF_PRIORS = 2766;

    static final int NUM_OF_CLASSES = 8;
    static final int NUM_OF_CORDINATES = 4;

    static final int NUM_LOC = NUM_OF_CORDINATES * NUM_OF_PRIORS;
    static final int NUM_CLASS = NUM_OF_CLASSES * NUM_OF_PRIORS;


    // Config values.
    private int inputSize;
    // Pre-allocated buffers.

    private int[] intValues;

    // outputLocations corresponds to the values of four co-ordinates of
    // all the priors
    float[][] outputLocations;

    // outputClasses corresponds to the values of the NUM_OF_CLASSES for all priors
    float[][] outputClasses;

    // This datastructure will be populated by the Interpreter

    private Map<Integer, Object> outputMap = new HashMap<>();


    /**
     * Initializes an {@code ImageClassifierFloatMobileNet}.
     *
     * @param context
     */
    SSDDetect(Context context) throws IOException {
        super(context);
        outputLocations = new float[1][NUM_LOC];
        outputClasses = new float[1][NUM_CLASS];
        outputMap.put(0, outputClasses);
        outputMap.put(1, outputLocations);


    }


    @Override
    MappedByteBuffer loadModelFile(Context context) throws IOException {
        return ModelFactory.sharedInstance.loadSSDDetectModelFile(context);
    }

    @Override
    protected int getImageSizeX() {
        return CROP_SIZE;
    }

    @Override
    protected int getImageSizeY() {
        return CROP_SIZE;
    }

    @Override
    protected int getNumBytesPerChannel() {
        return 4; // Float.SIZE / Byte.SIZE;
    }

    @Override
    protected void addPixelValue(int pixelValue) {
        imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_MEAN);
    }

    @Override
    protected void runInference() {
        Object[] inputArray = {imgData};
        Log.d("SSD Inference", "Running inference on image ");
        //final long startTime = SystemClock.uptimeMillis();
        tflite.runForMultipleInputsOutputs(inputArray, outputMap);
        //Log.d("SSD Inference", "Inference time: " + Long.toString(SystemClock.uptimeMillis() - startTime) );

    }
}
