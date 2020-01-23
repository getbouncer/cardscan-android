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
import java.util.List;

public class MultiModelMachineLearningThread extends MachineLearningThread {

    public class ModelResults {
        public final String panPrediction;
        public final Expiry expiry;
        public final List<DetectedSSDBox> objects;

        public ModelResults(String panPrediction, List<DetectedSSDBox> objects) {
            this.panPrediction = panPrediction;
            this.expiry = null;
            this.objects = objects;
        }
    }

    synchronized public void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                                  OnMultiListener multiListener, Context context, float roiCenterYRatio,
                                  File objectDetectFile, boolean runOcrModel, boolean runObjDetectionModel) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                multiListener, context, roiCenterYRatio, objectDetectFile, runOcrModel, runObjDetectionModel);
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

    private ObjectDetect runObjectDetection(final Bitmap bitmap, final RunArguments args,
                                            final Bitmap fullScreenBitmap) {
        final ObjectDetect detect = new ObjectDetect(args.mObjectDetectFile);
        final String result = detect.predictOnCpu(bitmap, args.mContext);
        return detect;
    }

    private void runOcrModel(final Bitmap bitmap, final RunArguments args,
                               final Bitmap bitmapForObjectDetection, final Bitmap fullScreenBitmap) {
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

        final Bitmap bm, fullScreen;
        if (args.mFrameBytes != null) {
            BitmapPair pair = getBitmap(args.mFrameBytes, args.mWidth, args.mHeight, args.mFormat,
                    args.mSensorOrientation, args.mRoiCenterYRatio, args.mIsOcr);
            bm = pair.cropped;
            fullScreen = pair.fullScreen;
        } else if (args.mBitmap != null) {
            bm = args.mBitmap;
            fullScreen = null;
        } else {
            bm = Bitmap.createBitmap(480, 480, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bm);
            Paint paint = new Paint();
            paint.setColor(Color.GRAY);
            canvas.drawRect(0.0f, 0.0f, 480.0f, 480.0f, paint);
            fullScreen = null;
        }

        final Bitmap croppedBitmap = cropBitmapForOCR(bm);
        final ObjectDetect detect;
        final boolean hadUnrecoverableException;
        final String number;
        if (args.mRunOcr) {
            Log.d("Steven serialMLThread", "running OCR");
            final SSDOcrDetect ocrDetect = new SSDOcrDetect();
            number = ocrDetect.predict(croppedBitmap, args.mContext);
            Log.d("OCR Detect", "OCR Number:" + number);
            hadUnrecoverableException = ocrDetect.hadUnrecoverableException;
        } else {
            hadUnrecoverableException = false;
            number = null;
        }
        if (args.mRunObjDetection) {
            Log.d("Steven serialMLThread", "running obj detection");
            detect = runObjectDetection(bm, args, fullScreen);
            Log.d("Steven serialMLThread", "done obj detection");
        } else {
            detect = null;
        }

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                Log.d("Steven debug", "handler OCR posting");

                try {
                    if (args.mMultiListener != null) {
                        if (hadUnrecoverableException) {
                            Log.d("Steven debug", "had fatal error...");
                            args.mMultiListener.onObjectFatalError();
                        } else {
                            Log.d("Steven debug", "sending prediction");
                            List<DetectedSSDBox> boxes = null;
                            if (detect != null) {
                                boxes = detect.objectBoxes;
                            }
                            args.mMultiListener.onMultiModelPrediction(
                                croppedBitmap, boxes, number, CreditCardUtils.isValidCardNumber(number), null, new ArrayList<DetectedBox>(),
                                    null, bm.getWidth(), bm.getHeight(), fullScreen);

                            Log.d("Steven debug", "done sending prediction");
                        }
                    } else {
                        Log.d("Steven debug", "null scan listener?!");
                    }

                    cleanup(bm, croppedBitmap, fullScreen);
                    Log.d("Steven debug", "finished cleanup");
                } catch (Error | Exception e) {
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
                runModelParallel();
            } catch (Error | Exception e) {
                // center field exception handling, make sure that the ml thread keeps running
                e.printStackTrace();
            }
        }
    }
}