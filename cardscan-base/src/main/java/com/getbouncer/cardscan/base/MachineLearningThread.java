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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.getbouncer.cardscan.base.image.YUVDecoder;
import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.LinkedList;

public class MachineLearningThread implements Runnable {

    protected static class RunArguments {
        @Nullable public final byte[] mFrameBytes;
        @Nullable public final Bitmap mBitmap;
        @Nullable final OnScanListener mScanListener;
        @Nullable final OnObjectListener mObjectListener;
        @Nullable public final OnUXModelListener mUXModelListener;
        @NonNull public final Context mContext;
        public final int mWidth;
        public final int mHeight;
        public final int mFormat;
        public final int mSensorOrientation;
        public final float mRoiCenterYRatio;
        public final boolean mIsOcr;
        @Nullable final File mObjectDetectFile;
        public final boolean mRunAdditionalOcr;
        public final boolean mRunUXModel;

        /**
         * Used by MachineLearningThread for running OCR on the main loop
         */
        RunArguments(
                @Nullable byte[] frameBytes,
                int width,
                int height,
                int format,
                int sensorOrientation,
                @Nullable OnScanListener scanListener,
                @NonNull Context context,
                float roiCenterYRatio
        ) {
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
            mRunAdditionalOcr = false;
            mRunUXModel = false;
            mUXModelListener = null;
        }

        /**
         * Used by MachineLearningThread for running Obj detection on the main loop
         */
        RunArguments(
                @Nullable byte[] frameBytes,
                int width,
                int height,
                int format,
                int sensorOrientation,
                @Nullable OnObjectListener objectListener,
                @NonNull Context context,
                float roiCenterYRatio,
                @Nullable File objectDetectFile
        ) {
            mFrameBytes = frameBytes;
            mBitmap = null;
            mWidth = width;
            mHeight = height;
            mFormat = format;
            mScanListener = null;
            mUXModelListener = null;
            mContext = context;
            mSensorOrientation = sensorOrientation;
            mRoiCenterYRatio = roiCenterYRatio;
            mIsOcr = false;
            mObjectListener = objectListener;
            mObjectDetectFile = objectDetectFile;
            mRunAdditionalOcr = false;
            mRunUXModel = false;
        }

        /**
         * Used by the new UXModelMachineLearningThread
         */
        public RunArguments(
                @NonNull byte[] frameBytes,
                int width,
                int height,
                int format,
                int sensorOrientation,
                @Nullable OnUXModelListener uxListener,
                @NonNull Context context,
                float roiCenterYRatio,
                @Nullable File objectDetectFile,
                boolean runOcrModel
        ) {
            this(frameBytes, width, height, format, sensorOrientation, uxListener, context,
                          roiCenterYRatio, objectDetectFile, runOcrModel, true);
        }

        /**
         * Used by the new UXModelMachineLearningThread
         */
        public RunArguments(
                @NonNull byte[] frameBytes,
                int width,
                int height,
                int format,
                int sensorOrientation,
                @Nullable OnUXModelListener uxListener,
                @NonNull Context context,
                float roiCenterYRatio,
                @Nullable File objectDetectFile,
                boolean runOcrModel,
                boolean runUXModel
        ) {
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
            mObjectListener = null;
            mObjectDetectFile = objectDetectFile;
            mRunAdditionalOcr = runOcrModel;
            mRunUXModel = runUXModel;
            mUXModelListener = uxListener;
        }


        /**
         * For testing OCR MLThread only
         */
        @VisibleForTesting
        RunArguments(
                @Nullable Bitmap bitmap,
                @Nullable OnScanListener scanListener,
                @NotNull Context context
        ) {
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
            mRunAdditionalOcr = false;
            mRunUXModel = false;
            mUXModelListener = null;
        }

        /**
         * For testing Object Detector MLThread only
         */
        @VisibleForTesting
        RunArguments(
                @Nullable Bitmap bitmap,
                @Nullable OnObjectListener objectListener,
                @NonNull Context context,
                @Nullable File objectDetectFile
        ) {
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
            mRunAdditionalOcr = false;
            mRunUXModel = false;
            mUXModelListener = null;
        }
    }

    protected static class Bitmaps {
        @NonNull public final Bitmap ocr;
        @NonNull public final Bitmap objectDetect;
        @NonNull public final Bitmap screenDetect;
        @NonNull public final Bitmap fullScreen;
        Bitmaps(
                @NonNull Bitmap ocr,
                @NonNull Bitmap objectDetect,
                @NonNull Bitmap screenDetect,
                @NonNull Bitmap fullScreen
        ) {
            this.ocr = ocr;
            this.objectDetect = objectDetect;
            this.screenDetect = screenDetect;
            this.fullScreen = fullScreen;
        }
    }

    protected LinkedList<RunArguments> queue = new LinkedList<>();

    public MachineLearningThread() {
        super();
    }

    public synchronized void warmUp(@NonNull Context context) {
        if (!queue.isEmpty()) {
            return;
        }
        RunArguments args = new RunArguments(null, 0, 0, 0,
                90, null, context, 0.5f);
        queue.push(args);
        notify();
    }

    synchronized public void post(
            @Nullable Bitmap bitmap,
            @Nullable OnScanListener scanListener,
            @NonNull Context context
    ) {
        RunArguments args = new RunArguments(bitmap, scanListener, context);
        queue.push(args);
        notify();
    }

    synchronized public void post(
            @NonNull byte[] bytes,
            int width,
            int height,
            int format,
            int sensorOrientation,
            @Nullable OnScanListener scanListener,
            @NonNull Context context,
            float roiCenterYRatio
    ) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                scanListener, context, roiCenterYRatio);
        queue.push(args);
        notify();
    }

    synchronized public void post(
            @Nullable Bitmap bitmap,
            @Nullable OnObjectListener objectListener,
            @NonNull Context context,
            @Nullable File objectDetectFile
    ) {
        RunArguments args = new RunArguments(bitmap, objectListener, context, objectDetectFile);
        queue.push(args);
        notify();
    }

    synchronized public void post(
            @NonNull byte[] bytes,
            int width,
            int height,
            int format,
            int sensorOrientation,
            @Nullable OnObjectListener objectListener,
            @NonNull Context context,
            float roiCenterYRatio,
            @Nullable File objectDetectFile
    ) {
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
    @NonNull
    public Bitmap YUVtoRGB(@NonNull byte[] yuvByteArray, int previewWidth, int previewHeight) {
        Bitmap fullImage = YUVDecoder.YUVtoBitmap(yuvByteArray, previewWidth, previewHeight);

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

    @NonNull
    protected Bitmaps getBitmaps(
            @NonNull byte[] bytes,
            int width,
            int height,
            int format,
            int sensorOrientation,
            float roiCenterYRatio,
            boolean isOcr
    ) {
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

        // create a bitmap of the desired size. crop is a square (h = w) with an offset matching the
        // card preview
        Bitmap objectCroppedBitmap = Bitmap.createBitmap(bitmap, x, y, (int) w, (int) h);

        long crop = SystemClock.uptimeMillis();
        if (GlobalConfig.PRINT_TIMING) {
            Log.d("MLThread", "crop -> " + ((crop - decode) / 1000.0));
        }

        // Rotate the cropped bitmap
        Matrix matrix = new Matrix();
        matrix.postRotate(sensorOrientation);
        Bitmap objectCroppedRotatedBitmap = Bitmap.createBitmap(objectCroppedBitmap, 0, 0, objectCroppedBitmap.getWidth(),
                objectCroppedBitmap.getHeight(), matrix, true);

        Bitmap ocrCroppedBitmap = cropBitmapForOCR(objectCroppedRotatedBitmap);

        Bitmap fullScreenRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(),
                bitmap.getHeight(), matrix, true);
        Bitmap screenCroppedRotatedBitmap = cropBitmapForScreenDetection(fullScreenRotated);

        long rotate = SystemClock.uptimeMillis();
        if (GlobalConfig.PRINT_TIMING) {
            Log.d("MLThread", "rotate -> " + ((rotate - crop) / 1000.0));
        }

        objectCroppedBitmap.recycle();
        bitmap.recycle();

        return new Bitmaps(ocrCroppedBitmap, objectCroppedRotatedBitmap, screenCroppedRotatedBitmap, fullScreenRotated);
    }

    @NonNull
    protected synchronized RunArguments getNextImage() {
        while (queue.size() == 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return queue.pop();
    }

    private void runObjectModel(
            @NonNull final Bitmap bitmapForObjectDetection,
            @NonNull final RunArguments args,
            @Nullable final Bitmap bitmapForScreenDetection
    ) {
        if (args.mObjectDetectFile == null) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (args.mObjectListener != null) {
                        args.mObjectListener.onPrediction(
                                bitmapForObjectDetection,
                                new LinkedList<DetectedSSDBox>(),
                                bitmapForObjectDetection.getWidth(),
                                bitmapForObjectDetection.getHeight(),
                                bitmapForScreenDetection
                        );
                    }
                    bitmapForObjectDetection.recycle();
                    if (bitmapForScreenDetection != null) {
                        bitmapForScreenDetection.recycle();
                    }
                }
            });
            return;
        }

        final ObjectDetect detect = new ObjectDetect(args.mObjectDetectFile);
        detect.predictOnCpu(bitmapForObjectDetection, args.mContext);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    if (args.mObjectListener != null) {
                        if (detect.hadUnrecoverableException) {
                            args.mObjectListener.onObjectFatalError();
                        } else {
                            args.mObjectListener.onPrediction(
                                    bitmapForObjectDetection,
                                    detect.objectBoxes,
                                    bitmapForObjectDetection.getWidth(),
                                    bitmapForObjectDetection.getHeight(),
                                    bitmapForScreenDetection
                            );
                        }
                    }
                    bitmapForObjectDetection.recycle();
                    if (bitmapForScreenDetection != null) {
                        bitmapForScreenDetection.recycle();
                    }
                } catch (Error | Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
            }
        });
    }

    private void runOcrModel(
            @NonNull final Bitmap bitmapForOcrDetection,
            @NonNull final RunArguments args,
            @NonNull final Bitmap bitmapForObjectDetection,
            @Nullable final Bitmap bitmapForScreenDetection
    ) {
        final SSDOcrDetect ocrDetect = new SSDOcrDetect();
        final String number = ocrDetect.predict(bitmapForOcrDetection, args.mContext);
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
                            args.mScanListener.onPrediction(
                                number,
                                null,
                                bitmapForOcrDetection,
                                null,
                                null,
                                bitmapForObjectDetection,
                                bitmapForScreenDetection
                            );
                        }
                    }
                    bitmapForOcrDetection.recycle();
                    bitmapForObjectDetection.recycle();
                    if (bitmapForScreenDetection != null) {
                        bitmapForScreenDetection.recycle();
                    }
                } catch (Error | Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
            }
        });
    }

    private void runModel() {
        @NonNull final RunArguments args = getNextImage();

        @NonNull Bitmap ocr, objectDetect;
        @Nullable Bitmap screenDetect;
        if (args.mFrameBytes != null) {
            Bitmaps bitmaps = getBitmaps(
                    args.mFrameBytes,
                    args.mWidth,
                    args.mHeight,
                    args.mFormat,
                    args.mSensorOrientation,
                    args.mRoiCenterYRatio,
                    args.mIsOcr
            );
            ocr = bitmaps.ocr;
            objectDetect = bitmaps.objectDetect;
            screenDetect = bitmaps.screenDetect;
        } else if (args.mBitmap != null) {
            ocr = cropBitmapForOCR(args.mBitmap);
            objectDetect = args.mBitmap;
            screenDetect = null;
        } else {

            objectDetect = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(objectDetect);
            Paint paint = new Paint();
            paint.setColor(Color.GRAY);
            canvas.drawRect(0.0f, 0.0f, 480.0f, 480.0f, paint);
            ocr = cropBitmapForOCR(objectDetect);
            screenDetect = null;
        }

        if (args.mIsOcr) {
            runOcrModel(ocr, args, objectDetect, screenDetect);
        } else {
            runObjectModel(objectDetect, args, screenDetect);
        }
    }

    @NonNull
    protected Bitmap cropBitmapForOCR(@NonNull Bitmap objectDetect) {
        float width = objectDetect.getWidth();
        float height = width * 375.0f / 600.0f;
        if (height > objectDetect.getHeight()) {
            height = objectDetect.getHeight();
            width = height / 375F * 600F;
        }
        float y = (objectDetect.getHeight() - height) / 2.0f;
        float x = (objectDetect.getWidth() - width) / 2F;
        return Bitmap.createBitmap(objectDetect, (int) x, (int) y, (int) width, (int) height);
    }

    @NonNull
    protected Bitmap cropBitmapForScreenDetection(@NonNull Bitmap fullScreen) {
        float width = fullScreen.getWidth();
        float height = width * 16F / 9F;
        if (height > fullScreen.getHeight()) {
            height = fullScreen.getHeight();
            width = height / 16F * 9F;
        }
        float y = (fullScreen.getHeight() - height) / 2F;
        float x = (fullScreen.getWidth() - width) / 2F;
        return Bitmap.createBitmap(fullScreen, (int) x, (int) y, (int) width, (int) height);
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
