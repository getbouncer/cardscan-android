package com.getbouncer.cardscan.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.VisibleForTesting;

import com.getbouncer.cardscan.base.image.YUVDecoder;
import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.io.File;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.LinkedList;

class MachineLearningThread implements Runnable {

    class RunArguments {
        private final byte[] mFrameBytes;
        private final Bitmap mBitmap;
        private final OnScanListener mScanListener;
        private final OnObjectListener mObjectListener;
        private final Context mContext;
        private final int mWidth;
        private final int mHeight;
        private final int mFormat;
        private final int mSensorOrientation;
        private final float mRoiCenterYRatio;
        private final boolean mIsOcr;
        private final File mObjectDetectFile;

        RunArguments(byte[] frameBytes, int width, int height, int format,
                     int sensorOrientation, OnScanListener scanListener, Context context,
                     float roiCenterYRatio) {
            mFrameBytes = frameBytes;
            mBitmap = null;
            mWidth = width;
            mHeight = height;
            mFormat = format;
            mScanListener = scanListener;
            mContext = context;
            mSensorOrientation = sensorOrientation;
            mRoiCenterYRatio = roiCenterYRatio;
            mIsOcr = true;
            mObjectListener = null;
            mObjectDetectFile = null;
        }

        RunArguments(byte[] frameBytes, int width, int height, int format,
                     int sensorOrientation, OnObjectListener objectListener, Context context,
                     float roiCenterYRatio, File objectDetectFile) {
            mFrameBytes = frameBytes;
            mBitmap = null;
            mWidth = width;
            mHeight = height;
            mFormat = format;
            mScanListener = null;
            mContext = context;
            mSensorOrientation = sensorOrientation;
            mRoiCenterYRatio = roiCenterYRatio;
            mIsOcr = false;
            mObjectListener = objectListener;
            mObjectDetectFile = objectDetectFile;
        }

        @VisibleForTesting
        RunArguments(Bitmap bitmap, OnScanListener scanListener, Context context) {
            mFrameBytes = null;
            mBitmap = bitmap;
            mWidth = bitmap == null ? 0 : bitmap.getWidth();
            mHeight = bitmap == null ? 0 : bitmap.getHeight();
            mFormat = 0;
            mScanListener = scanListener;
            mContext = context;
            mSensorOrientation = 0;
            mRoiCenterYRatio = 0;
            mIsOcr = true;
            mObjectListener = null;
            mObjectDetectFile = null;
        }

        @VisibleForTesting
        RunArguments(Bitmap bitmap, OnObjectListener objectListener, Context context,
                     File objectDetectFile) {
            mFrameBytes = null;
            mBitmap = bitmap;
            mWidth = bitmap == null ? 0 : bitmap.getWidth();
            mHeight = bitmap == null ? 0 : bitmap.getHeight();
            mFormat = 0;
            mScanListener = null;
            mContext = context;
            mSensorOrientation = 0;
            mRoiCenterYRatio = 0;
            mIsOcr = false;
            mObjectListener = objectListener;
            mObjectDetectFile = objectDetectFile;
        }
    }

    class BitmapPair {
        private Bitmap cropped;
        private Bitmap fullScreen;
        BitmapPair(Bitmap cropped, Bitmap fullScreen) {
            this.cropped = cropped;
            this.fullScreen = fullScreen;
        }
    }

    private LinkedList<RunArguments> queue = new LinkedList<>();

    MachineLearningThread() {
        super();
    }

    public synchronized void warmUp(Context context) {
        if (Ocr.isInit() || !queue.isEmpty()) {
            return;
        }
        RunArguments args = new RunArguments(null, 0, 0, 0,
                90, null, context, 0.5f);
        queue.push(args);
        notify();
    }

    synchronized void post(Bitmap bitmap, OnScanListener scanListener, Context context) {
        RunArguments args = new RunArguments(bitmap, scanListener, context);
        queue.push(args);
        notify();
    }

    synchronized void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                           OnScanListener scanListener, Context context, float roiCenterYRatio) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                scanListener, context, roiCenterYRatio);
        queue.push(args);
        notify();
    }

    synchronized void post(Bitmap bitmap, OnObjectListener objectListener, Context context,
                           File objectDetectFile) {
        RunArguments args = new RunArguments(bitmap, objectListener, context, objectDetectFile);
        queue.push(args);
        notify();
    }

    synchronized void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                           OnObjectListener objectListener, Context context, float roiCenterYRatio,
                           File objectDetectFile) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                objectListener, context, roiCenterYRatio, objectDetectFile);
        queue.push(args);
        notify();
    }

    /**
     * from https://stackoverflow.com/questions/8340128/decoding-yuv-to-rgb-in-c-c-with-ndk
     * This appears to be a fairly common problem for image processing apps. See also the native
     * implementation of YUVtoARGB https://github.com/cats-oss/android-gpuimage/blob/master/library/src/main/cpp/yuv-decoder.c
     */
    public Bitmap YUVtoRGB(byte[] yuvByteArray, int previewWidth, int previewHeight) {
        int[] argbByteArray = new int[previewWidth * previewHeight];
        YUVDecoder.YUVtoRGBA(yuvByteArray, previewWidth, previewHeight, argbByteArray);

        Bitmap fullImage = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);
        fullImage.copyPixelsFromBuffer(IntBuffer.wrap(argbByteArray));

        int resizedWidth, resizedHeight;
        if (previewWidth > previewHeight) {
            resizedHeight = ScanBaseActivity.MIN_IMAGE_EDGE;
            resizedWidth = previewWidth * resizedHeight / previewHeight;
        } else {
            resizedWidth = ScanBaseActivity.MIN_IMAGE_EDGE;
            resizedHeight = previewHeight * resizedWidth / previewWidth;
        }

        Bitmap resizedImage = Bitmap.createScaledBitmap(fullImage, resizedWidth, resizedHeight, false);
        fullImage.recycle();

        return resizedImage;
    }

    private BitmapPair getBitmap(byte[] bytes, int width, int height, int format,
                                 int sensorOrientation, float roiCenterYRatio, boolean isOcr) {
        long startTime = SystemClock.uptimeMillis();

        final Bitmap bitmap = YUVtoRGB(bytes, width, height);
        long decode = SystemClock.uptimeMillis();

        if (GlobalConfig.PRINT_TIMING) {
            Log.d("MLThread", "decode -> " + ((decode - startTime) / 1000.0));
        }

        sensorOrientation = sensorOrientation % 360;

        double h;
        double w;
        int x;
        int y;

        if (sensorOrientation == 0) {
            w = bitmap.getWidth();
            h = w;
            x = 0;
            y = (int) Math.round(((double) bitmap.getHeight()) * roiCenterYRatio - h * 0.5);
        } else if (sensorOrientation == 90) {
            h = bitmap.getHeight();
            w = h;
            y = 0;
            x = (int) Math.round(((double) bitmap.getWidth()) * roiCenterYRatio - w * 0.5);
        } else if (sensorOrientation == 180) {
            w = bitmap.getWidth();
            h = w;
            x = 0;
            y = (int) Math.round(((double) bitmap.getHeight()) * (1.0 - roiCenterYRatio) - h * 0.5);
        } else {
            h = bitmap.getHeight();
            w = h;
            x = (int) Math.round(((double) bitmap.getWidth()) * (1.0 - roiCenterYRatio) - w * 0.5);
            y = 0;
        }

        // make sure that our crop stays within the image
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if ((x+w) > bitmap.getWidth()) {
            x = bitmap.getWidth() - (int) w;
        }
        if ((y+h) > bitmap.getHeight()) {
            y = bitmap.getHeight() - (int) h;
        }

        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, x, y, (int) w, (int) h);

        long crop = SystemClock.uptimeMillis();
        if (GlobalConfig.PRINT_TIMING) {
            Log.d("MLThread", "crop -> " + ((crop - decode) / 1000.0));
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(sensorOrientation);
        Bitmap bm = Bitmap.createBitmap(croppedBitmap, 0, 0, croppedBitmap.getWidth(),
                croppedBitmap.getHeight(), matrix, true);


        Bitmap fullScreen = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);

        long rotate = SystemClock.uptimeMillis();
        if (GlobalConfig.PRINT_TIMING) {
            Log.d("MLThread", "rotate -> " + ((rotate - crop) / 1000.0));
        }

        croppedBitmap.recycle();
        bitmap.recycle();

        return new BitmapPair(bm, fullScreen);
    }

    private synchronized RunArguments getNextImage() {
        while (queue.size() == 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return queue.pop();
    }

    private void runObjectModel(final Bitmap bitmap, final RunArguments args,
                                final Bitmap fullScreenBitmap) {
        if (args.mObjectDetectFile == null) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (args.mObjectListener != null) {
                        args.mObjectListener.onPrediction(bitmap, new LinkedList<DetectedSSDBox>(),
                                bitmap.getWidth(), bitmap.getHeight(), fullScreenBitmap);
                    }
                    bitmap.recycle();
                    fullScreenBitmap.recycle();
                }
            });
            return;
        }

        final ObjectDetect detect = new ObjectDetect(args.mObjectDetectFile);
        final String result = detect.predictOnCpu(bitmap, args.mContext);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    if (args.mObjectListener != null) {
                        if (detect.hadUnrecoverableException) {
                            args.mObjectListener.onObjectFatalError();
                        } else {
                            args.mObjectListener.onPrediction(bitmap, detect.objectBoxes,
                                    bitmap.getWidth(), bitmap.getHeight(), fullScreenBitmap);
                        }
                    }
                    bitmap.recycle();
                    fullScreenBitmap.recycle();
                } catch (Error | Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
            }
        });
    }

    private void runOcrModel(final Bitmap bitmap, final RunArguments args,
                             final Bitmap bitmapForObjectDetection, final Bitmap fullScreenBitmap) {
        //final Ocr ocr = new Ocr();
        //final String number = ocr.predict(bitmap, args.mContext);
        final SSDOcrDetect ocrDetect = new SSDOcrDetect();
        final String number = ocrDetect.predict(bitmap, args.mContext);
        Log.d("OCR Detect", "OCR Number:" + number);
        final boolean hadUnrecoverableException = ocrDetect.hadUnrecoverableException;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    if (args.mScanListener != null) {
                        if (hadUnrecoverableException) {
                            args.mScanListener.onFatalError();
                        } else {
                            args.mScanListener.onPrediction(number, null, bitmap, new ArrayList<DetectedBox>(),
                                    null, bitmapForObjectDetection, fullScreenBitmap);
                        }
                    }
                    bitmap.recycle();
                    bitmapForObjectDetection.recycle();
                    if (fullScreenBitmap != null) {
                        fullScreenBitmap.recycle();
                    }
                } catch (Error | Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
            }
        });
    }

    private void runModel() {
        final RunArguments args = getNextImage();

        Bitmap bm, fullScreen = null;
        if (args.mFrameBytes != null) {
            BitmapPair pair = getBitmap(args.mFrameBytes, args.mWidth, args.mHeight, args.mFormat,
                    args.mSensorOrientation, args.mRoiCenterYRatio, args.mIsOcr);
            bm = pair.cropped;
            fullScreen = pair.fullScreen;
        } else if (args.mBitmap != null) {
            bm = args.mBitmap;
        } else {
            bm = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm);
            Paint paint = new Paint();
            paint.setColor(Color.GRAY);
            canvas.drawRect(0.0f, 0.0f, 480.0f, 480.0f, paint);
        }

        if (args.mIsOcr) {
            float width = bm.getWidth();
            float height = width * 375.0f / 600.0f;
            float y = (bm.getHeight() - height) / 2.0f;
            float x = 0.0f;
            Bitmap croppedBitmap = Bitmap.createBitmap(bm, (int) x, (int) y, (int) width,
                    (int) height);

            runOcrModel(croppedBitmap, args, bm, fullScreen);
        } else {
            runObjectModel(bm, args, fullScreen);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                runModel();
            } catch (Error | Exception e) {
                // center field exception handling, make sure that the ml thread keeps running
                e.printStackTrace();
            }
        }
    }
}
