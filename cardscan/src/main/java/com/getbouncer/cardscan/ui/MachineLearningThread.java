package com.getbouncer.cardscan.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;

import com.getbouncer.cardscan.Ocr;

import java.io.ByteArrayOutputStream;
import java.util.LinkedList;

public class MachineLearningThread implements Runnable {

    class RunArguments {
        private final byte[] mFrameBytes;
        private final ScanActivity mActivity;
        private final int mWidth;
        private final int mHeight;
        private final int mFormat;
        private final int mSensorOrientation;

        RunArguments(byte[] frameBytes, int width, int height, int format,
                     int sensorOrientation, ScanActivity activity) {
            mFrameBytes = frameBytes;
            mWidth = width;
            mHeight = height;
            mFormat = format;
            mActivity = activity;
            mSensorOrientation = sensorOrientation;
        }
    }

    private LinkedList<RunArguments> queue = new LinkedList<>();

    synchronized void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                           ScanActivity activity) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                activity);
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

    private synchronized void runModel() {
        while (queue.size() == 0) {
            try {
                wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        final RunArguments args = queue.pop();
        final Bitmap bitmap = getBitmap(args.mFrameBytes, args.mWidth, args.mHeight, args.mFormat,
                args.mSensorOrientation);

        Ocr ocr = new Ocr();
        final String number = ocr.predict(bitmap, args.mActivity);
        args.mActivity.onPrediction(number, ocr.expiry, bitmap, ocr.digitBoxes, ocr.expiryBox);
    }

    @Override
    public void run() {
        while (true) {
            runModel();
        }
    }
}
