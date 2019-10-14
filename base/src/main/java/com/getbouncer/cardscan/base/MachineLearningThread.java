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
import android.util.Log;
import android.view.Display;

import java.io.ByteArrayOutputStream;
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
        }

        RunArguments(byte[] frameBytes, int width, int height, int format,
                     int sensorOrientation, OnObjectListener objectListener, Context context,
                     float roiCenterYRatio) {
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
            mIsOcr = true;
            mObjectListener = null;
        }

        // this should only be used for testing
        RunArguments(Bitmap bitmap, OnObjectListener objectListener, Context context) {
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
                90, (OnScanListener) null, context, 0.5f);
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

    synchronized void post(Bitmap bitmap, OnObjectListener objectListener, Context context) {
        RunArguments args = new RunArguments(bitmap, objectListener, context);
        queue.push(args);
        notify();
    }

    synchronized void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                           OnObjectListener objectListener, Context context, float roiCenterYRatio) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                objectListener, context, roiCenterYRatio);
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

    private void runObjectModel(final Bitmap bitmap, final RunArguments args) {
        // don't do anything for now
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    if (args.mObjectListener != null) {
                        /*
                        if (hadUnrecoverableException) {
                            args.mScanListener.onFatalError();
                        } else {
                            args.mScanListener.onPrediction(number, ocr.expiry, bitmap, ocr.digitBoxes,
                                    ocr.expiryBox);
                        }*/
                        args.mObjectListener.onPrediction(bitmap);
                    }
                } catch (Error | Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
            }
        });
    }

    private void runOcrModel(final Bitmap bitmap, final RunArguments args) {
        final Ocr ocr = new Ocr();
        final String number = ocr.predict(bitmap, args.mContext);
        final ObjectDetect objectDetect = new ObjectDetect();
        final String status = objectDetect.predict(bitmap, args.mContext);
        Log.e("Object Detect", "runModel: Ran object detect ");
        final boolean hadUnrecoverableException = ocr.hadUnrecoverableException;
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                try {
                    if (args.mScanListener != null) {
                        if (hadUnrecoverableException) {
                            args.mScanListener.onFatalError();
                        } else {
                            args.mScanListener.onPrediction(number, ocr.expiry, bitmap, ocr.digitBoxes,
                                    ocr.expiryBox, objectDetect.objectBoxes);
                        }
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

        if (args.mIsOcr) {
            runOcrModel(bm, args);
        } else {
            runObjectModel(bm, args);
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
