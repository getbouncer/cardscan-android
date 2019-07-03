package com.getbouncer.cardscan.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;

import java.io.ByteArrayOutputStream;
import java.util.LinkedList;

class MachineLearningThread implements Runnable {

    class RunArguments {
        private final byte[] mFrameBytes;
        private final Bitmap mBitmap;
        private final OnScanListener mScanListener;
        private final Context mContext;
        private final int mWidth;
        private final int mHeight;
        private final int mFormat;
        private final int mSensorOrientation;
        private final float mRoiCenterYRatio;

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
        }

        // this should only be used for testing
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
                90,null, context, 0.5f);
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

    private Bitmap getBitmap(byte[] bytes, int width, int height, int format, int sensorOrientation,
                             float roiCenterYRatio) {
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
        int y = (int) Math.round(((double) bm.getHeight()) * roiCenterYRatio - h * 0.5);

        Bitmap result = Bitmap.createBitmap(bm, x, y, (int) w, (int) h);
        bitmap.recycle();
        bm.recycle();

        return result;
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

    private void runModel() {
        final RunArguments args = getNextImage();

        Bitmap bm;
        if (args.mFrameBytes != null) {
            bm = getBitmap(args.mFrameBytes, args.mWidth, args.mHeight, args.mFormat,
                    args.mSensorOrientation, args.mRoiCenterYRatio);
        } else if (args.mBitmap != null) {
            bm = args.mBitmap;
        } else {
            bm = Bitmap.createBitmap(480, 302, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm);
            Paint paint = new Paint();
            paint.setColor(Color.GRAY);
            canvas.drawRect(0.0f, 0.0f, 480.0f, 302.0f, paint);
        }

        final Bitmap bitmap = bm;

        final Ocr ocr = new Ocr();
        final String number = ocr.predict(bitmap, args.mContext);
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    if (args.mScanListener != null) {
                        args.mScanListener.onPrediction(number, ocr.expiry, bitmap, ocr.digitBoxes,
                                ocr.expiryBox);
                    }
                } catch (Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
                bitmap.recycle();
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
