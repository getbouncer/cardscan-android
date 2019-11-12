/* Copyright 2017 The TensorFlow Authors. All Rights Reserved.

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
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import org.tensorflow.lite.Interpreter;

/**
 * Classifies images with Tensorflow Lite.
 */
abstract class ImageClassifier {
    /** Tag for the {@link Log}. */
    private static final String TAG = "CardScan";

    /** Dimensions of inputs. */
    private static final int DIM_BATCH_SIZE = 1;

    private static final int DIM_PIXEL_SIZE = 3;

    /** Preallocated buffers for storing image data in. */
    private int[] intValues = new int[getImageSizeX() * getImageSizeY()];

    /** Options for configuring the Interpreter. */
    private final Interpreter.Options tfliteOptions = new Interpreter.Options();

    /** The loaded TensorFlow Lite model. */
    private MappedByteBuffer tfliteModel;

    /** An instance of the driver class to run model inference with Tensorflow Lite. */
    protected Interpreter tflite;

    /** A ByteBuffer to hold image data, to be feed into Tensorflow Lite as inputs. */
    protected ByteBuffer imgData = null;


    /** Initializes an {@code ImageClassifier}. */
    ImageClassifier(Context context) throws IOException {
        init(context);
    }

    ImageClassifier() {
        // don't do anything, but make sure that you call init later
    }

    void init(Context context) throws IOException {
        tfliteModel = loadModelFile(context);
        tflite = new Interpreter(tfliteModel, tfliteOptions);
        imgData =
                ByteBuffer.allocateDirect(
                        DIM_BATCH_SIZE
                                * getImageSizeX()
                                * getImageSizeY()
                                * DIM_PIXEL_SIZE
                                * getNumBytesPerChannel());
        imgData.order(ByteOrder.nativeOrder());
    }

    /** Classifies a frame from the preview stream. */
    void classifyFrame(Bitmap bitmap) {
        if (tflite == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.");
        }
        convertBitmapToByteBuffer(bitmap);
        // Here's where the magic happens!!!
        long startTime = SystemClock.uptimeMillis();
        runInference();
        long endTime = SystemClock.uptimeMillis();
        //Log.d(TAG, "Timecost to run model inference: " + Long.toString(endTime - startTime));
    }

    private void recreateInterpreter() {
        if (tflite != null) {
            tflite.close();
            tflite = new Interpreter(tfliteModel, tfliteOptions);
        }
    }

    public void useCPU() {
        tfliteOptions.setUseNNAPI(false);
        recreateInterpreter();
    }

    public void useNNAPI() {
        tfliteOptions.setUseNNAPI(true);
        recreateInterpreter();
    }

    public void setNumThreads(int numThreads) {
        tfliteOptions.setNumThreads(numThreads);
        recreateInterpreter();
    }

    /** Closes tflite to release resources. */
    public void close() {
        tflite.close();
        tflite = null;
        tfliteModel = null;
    }

    /** Memory-map the model file in Assets. */
    abstract MappedByteBuffer loadModelFile(Context context) throws IOException;

    /** Writes Image data into a {@code ByteBuffer}. */
    private void convertBitmapToByteBuffer(Bitmap bitmap) {
        if (imgData == null) {
            return;
        }
        imgData.rewind();

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, getImageSizeX(), getImageSizeY(), false);
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.getWidth(), 0, 0,
                resizedBitmap.getWidth(), resizedBitmap.getHeight());
        // Convert the image to floating point.
        int pixel = 0;
        long startTime = SystemClock.uptimeMillis();
        for (int i = 0; i < getImageSizeX(); ++i) {
            for (int j = 0; j < getImageSizeY(); ++j) {
                final int val = intValues[pixel++];
                addPixelValue(val);
            }
        }
        long endTime = SystemClock.uptimeMillis();
        //Log.d(TAG, "Timecost to put values into ByteBuffer: " + Long.toString(endTime - startTime));
    }

    /**
     * Get the image size along the x axis.
     *
     * @return
     */
    protected abstract int getImageSizeX();

    /**
     * Get the image size along the y axis.
     *
     * @return
     */
    protected abstract int getImageSizeY();

    /**
     * Get the number of bytes that is used to store a single color channel value.
     *
     * @return
     */
    protected abstract int getNumBytesPerChannel();

    /**
     * Add pixelValue to byteBuffer.
     *
     * @param pixelValue
     */
    protected abstract void addPixelValue(int pixelValue);

    /**
     * Run inference using the prepared input in {@link #imgData}. Afterwards, the result will be
     * provided by getProbability().
     *
     * <p>This additional method is necessary, because we don't have a common base for different
     * primitive data types.
     */
    protected abstract void runInference();
}