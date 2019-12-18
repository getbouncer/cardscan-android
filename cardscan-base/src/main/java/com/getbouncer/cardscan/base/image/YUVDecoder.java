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

public class YUVDecoder {
    static {
        System.loadLibrary("yuv-decoder");
    }

    /**
     * Convert a YUV nv21 image byte array to an RGBA image byte array
     * @param yuv YUV nv21 image data
     * @param width width of the source image
     * @param height height of the source image
     * @param out RGBA image data
     */
    public static native void YUVtoRGBA(byte[] yuv, int width, int height, int[] out);

    /**
     * Convert a YUV nv21 image byte array to an ARGB image byte array
     * @param yuv YUV nv21 image data
     * @param width width of the source image
     * @param height height of the source image
     * @param out RGBA image data
     */
    public static native void YUVtoARGB(byte[] yuv, int width, int height, int[] out);
}
