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
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.io.File;
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
            mObjectDetectFile = null;
        }

        // this should only be used for testing
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

    // from https://stackoverflow.com/questions/43623817/android-yuv-nv12-to-rgb-conversion-with-renderscript
    // interestingly the question had the right algorithm for our format (yuv nv21)
    public Bitmap YUV_toRGB(byte[] yuvByteArray,int W,int H, Context ctx) {
        RenderScript rs = RenderScript.create(ctx);
        ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs,
                Element.U8_4(rs));

        Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs)).setX(yuvByteArray.length);
        Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

        Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(W).setY(H);
        Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);

        in.copyFrom(yuvByteArray);

        yuvToRgbIntrinsic.setInput(in);
        yuvToRgbIntrinsic.forEach(out);
        Bitmap fullImage = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);

        out.copyTo(fullImage);

        yuvToRgbIntrinsic.destroy();
        rs.destroy();
        in.destroy();
        out.destroy();

        int width, height;
        if (W > H) {
            height = ScanBaseActivity.MIN_IMAGE_EDGE;
            width = W * height / H;
        } else {
            width = ScanBaseActivity.MIN_IMAGE_EDGE;
            height = H * width / W;
        }
        Bitmap resizedImage = Bitmap.createScaledBitmap(fullImage, width, height, false);
        fullImage.recycle();
        fullImage = null;

        return resizedImage;
    }

    private Bitmap getBitmap(byte[] bytes, int width, int height, int format, int sensorOrientation,
                             float roiCenterYRatio, Context ctx, boolean isOcr) {
        long startTime = SystemClock.uptimeMillis();

        final Bitmap bitmap = YUV_toRGB(bytes, width, height, ctx);
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
        Bitmap bm = Bitmap.createBitmap(croppedBitmap, 0, 0, croppedBitmap.getWidth(), croppedBitmap.getHeight(),
                matrix, true);


        long rotate = SystemClock.uptimeMillis();
        if (GlobalConfig.PRINT_TIMING) {
            Log.d("MLThread", "rotate -> " + ((rotate - crop) / 1000.0));
        }

        croppedBitmap.recycle();
        bitmap.recycle();

        return bm;
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
        if (args.mObjectDetectFile == null) {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (args.mObjectListener != null) {
                        args.mObjectListener.onPrediction(bitmap, new LinkedList<DetectedSSDBox>(),
                                bitmap.getWidth(), bitmap.getHeight());
                    }
                    bitmap.recycle();
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
                                    bitmap.getWidth(), bitmap.getHeight());
                        }
                    }
                } catch (Error | Exception e) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace();
                }
            }
        });
    }

    private void runOcrModel(final Bitmap bitmap, final RunArguments args,
                             final Bitmap bitmapForObjectDetection) {
        final Ocr ocr = new Ocr();
        final String number = ocr.predict(bitmap, args.mContext);
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
                                    ocr.expiryBox, bitmapForObjectDetection);
                        }
                    }
                    bitmap.recycle();
                    bitmapForObjectDetection.recycle();
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
                    args.mSensorOrientation, args.mRoiCenterYRatio, args.mContext, args.mIsOcr);
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
            float height = width * 302.0f / 480.0f;
            float y = (bm.getHeight() - height) / 2.0f;
            float x = 0.0f;
            Bitmap croppedBitmap = Bitmap.createBitmap(bm, (int) x, (int) y, (int) width,
                    (int) height);

            runOcrModel(croppedBitmap, args, bm);
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
