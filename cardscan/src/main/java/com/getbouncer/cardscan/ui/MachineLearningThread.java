package com.getbouncer.cardscan.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Looper;

import com.getbouncer.cardscan.Ocr;

import java.io.ByteArrayOutputStream;
import java.util.LinkedList;

public class MachineLearningThread implements Runnable {

    class RunArguments {
        private final byte[] mFrameBytes;
        private final OnScanListener mScanListener;
        private final Context mContext;
        private final int mWidth;
        private final int mHeight;
        private final int mFormat;
        private final int mSensorOrientation;

        RunArguments(byte[] frameBytes, int width, int height, int format,
                     int sensorOrientation, OnScanListener scanListener, Context context) {
            mFrameBytes = frameBytes;
            mWidth = width;
            mHeight = height;
            mFormat = format;
            mScanListener = scanListener;
            mContext = context;
            mSensorOrientation = sensorOrientation;
        }
    }

    private LinkedList<RunArguments> queue = new LinkedList<>();

    synchronized void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                           OnScanListener scanListener, Context context) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                scanListener, context);
        queue.push(args);
        notify();
    }

    private Bitmap getBitmap(byte[] bytes, int width, int height, int format, int sensorOrientation) {
        YuvImage yuv = new YuvImage(bytes, format, width, height, null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuv.compressToJpeg(new Rect(0, 0, width, height), 100, out);

        byte[] b = out.toByteArray();
        final Bitmap bitmap = BitmapFactory.decodeByteArray(b, 0, b.length);

        Matrix matrix = new Matrix();
        matrix.postRotate(sensorOrientation);
        Bitmap bm = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(),
                matrix, true);


        double w = bm.getWidth();
        double h = 302.0 * w / 480.0;
        int x = 0;
        int y = (int) Math.round(((double) bm.getHeight()) * 0.5 - h * 0.5);
        return Bitmap.createBitmap(bm, x, y, (int) w, (int) h);
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

    private synchronized void runModel() {
        final RunArguments args = getNextImage();
        final Bitmap bitmap = getBitmap(args.mFrameBytes, args.mWidth, args.mHeight, args.mFormat,
                args.mSensorOrientation);

        final Ocr ocr = new Ocr();
        final String number = ocr.predict(bitmap, args.mContext);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    args.mScanListener.onPrediction(number, ocr.expiry, bitmap, ocr.digitBoxes,
                            ocr.expiryBox);
                } catch (Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void run() {
        while (true) {
            try {
                runModel();
            } catch (Exception e) {
                // center field exception handling, make sure that the ml thread keeps running
                e.printStackTrace();
            }
        }
    }
}
