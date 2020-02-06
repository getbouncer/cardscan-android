/*
 * Copyright (C) 2018 CyberAgent, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file was modified from https://github.com/cats-oss/android-gpuimage/blob/cc421fd1fc9f0a1bc56396066b942724e3b8d9ba/library/src/main/java/jp/co/cyberagent/android/gpuimage/GPUImageNativeLibrary.java
 */

package com.getbouncer.cardscan.base.image;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;

import java.io.ByteArrayOutputStream;

public class YUVDecoder {

    /**
     * Convert a YUV nv21 image byte array to a Bitmap.
     * @param data YUV nv21 image data
     * @param width width of the source image
     * @param height height of the source image
     * @return the converted Bitmap
     */
    public static Bitmap YUVtoBitmap(byte[] data, int width, int height) {
        YuvImage yuv = new YuvImage(data, ImageFormat.NV21, width, height, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, width, height), 50, out);

        byte[] bytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
