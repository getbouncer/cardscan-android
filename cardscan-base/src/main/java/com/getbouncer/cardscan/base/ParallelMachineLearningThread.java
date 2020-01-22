package com.getbouncer.cardscan.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;

public class ParallelMachineLearningThread extends MachineLearningThread {

    synchronized public void post(Bitmap bitmap, OnScanListener onScanListener, OnObjectListener objectListener, Context context,
                                  File objectDetectFile, boolean runOcrModel, boolean runObjDetectionModel) {
        RunArguments args = new RunArguments(bitmap, objectListener, context, objectDetectFile);
        queue.push(args);
        notify();
    }

    synchronized public void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                                  OnScanListener onScanListener,
                                  OnObjectListener objectListener, Context context, float roiCenterYRatio,
                                  File objectDetectFile, boolean runOcrModel, boolean runObjDetectionModel) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                onScanListener, objectListener, context, roiCenterYRatio, objectDetectFile, runOcrModel, runObjDetectionModel);
        queue.push(args);
        notify();
    }

    private Thread runObjectModel(final Bitmap bitmap, final RunArguments args,
                                final Bitmap fullScreenBitmap) {
        return new Thread() {
            public void run() {
                if (args.mObjectDetectFile == null) {
                    Handler handler = new Handler(Looper.getMainLooper());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (args.mObjectListener != null) {
                                args.mObjectListener.onPrediction(bitmap, new LinkedList<DetectedSSDBox>(),
                                        bitmap.getWidth(), bitmap.getHeight(), fullScreenBitmap);
                            }
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
                        } catch (Error | Exception e) {
                            // prevent callbacks from crashing the app, swallow it
                            e.printStackTrace();
                        }
                    }
                });
            }
        };
    }

    private Thread runOcrModel(final Bitmap bitmap, final RunArguments args,
                             final Bitmap bitmapForObjectDetection, final Bitmap fullScreenBitmap) {
        return new Thread() {
            public void run() {
                final SSDOcrDetect ocrDetect = new SSDOcrDetect();
                final String number = ocrDetect.predict(bitmap, args.mContext);
                Log.d("OCR Detect", "OCR Number:" + number);
                final boolean hadUnrecoverableException = ocrDetect.hadUnrecoverableException;
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    public void run() {
                        Log.d("Steven debug", "handler OCR posting");
                        try {

                            if (args.mScanListener != null) {
                                if (hadUnrecoverableException) {
                                    Log.d("Steven debug", "had fatal error...");
                                    args.mScanListener.onFatalError();
                                } else {
                                    Log.d("Steven debug", "sending prediction");
                                    args.mScanListener.onPrediction(number, null, bitmap, new ArrayList<DetectedBox>(),
                                            null, bitmapForObjectDetection, fullScreenBitmap);
                                    Log.d("Steven debug", "done sending prediction");
                                }
                            } else {
                                Log.d("Steven debug", "null scan listener?!");
                            }
                            cleanup(bitmap, null, fullScreenBitmap);
                            Log.d("Steven debug", "finished cleanup");
                        } catch (Error | Exception e) {
                            // prevent callbacks from crashing the app, swallow it
                            e.printStackTrace();
                        }
                    }
                });
            }
        };
    }

    private static void cleanup(Bitmap bitmap, Bitmap bitmapForObjectDetection, Bitmap fullScreenBitmap) {
        if (bitmap != null) {
            bitmap.recycle();
        }
        if (bitmapForObjectDetection != null) {
            bitmapForObjectDetection.recycle();
        }
        if (fullScreenBitmap != null) {
            fullScreenBitmap.recycle();
        }
    }

    private void runModelParallel() {
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


        Bitmap croppedBitmap = cropBitmapForOCR(bm);
        Thread ocrThread = null, objThread = null;
        if (args.mRunOcr) {
            Log.d("Steven ParallelMLThread", "running OCR");
            ocrThread = runOcrModel(croppedBitmap, args, bm, fullScreen);
            ocrThread.start();
        }
        if (args.mRunObjDetection) {
            Log.d("Steven lMLThread", "running obj detection");
            objThread = runObjectModel(bm, args, fullScreen);
            objThread.start();
        }
        try {
            if (ocrThread != null) {
                Log.d("Steven ParallelMLThread", "waitinf for OCR");
                ocrThread.join();
                Log.d("Steven ParallelMLThread", "finished waitinf for OCR");
            }
            if (objThread != null) {
                Log.d("Steven ParallelMLThread", "waitinf for obj detect");
                objThread.join();
                Log.d("Steven ParallelMLThread", "finished waitinf for obj detect");

            }
            // cleanup(croppedBitmap, bm, fullScreen);
        } catch (InterruptedException exp) {
            return;
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                runModelParallel();
            } catch (Error | Exception e) {
                // center field exception handling, make sure that the ml thread keeps running
                e.printStackTrace();
            }
        }
    }
}
