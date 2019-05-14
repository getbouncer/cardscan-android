package com.getbouncer.cardscan.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import com.getbouncer.cardscan.DetectedBox;
import com.getbouncer.cardscan.Expiry;
import com.getbouncer.cardscan.ImageUtils;
import com.getbouncer.cardscan.Ocr;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MachineLearningThread implements Runnable {

    class RunArguments {
        private final byte[] mFrameBytes;
        private final Semaphore mSemaphore;
        private final ImageView mImageView;
        private final Activity mActivity;
        private final int mWidth;
        private final int mHeight;
        private final int mFormat;
        private final int mSensorOrientation;

        RunArguments(byte[] frameBytes, int width, int height, int format,
                     int sensorOrientation, Semaphore semaphore, Activity activity,
                     ImageView imageView) {
            mFrameBytes = frameBytes;
            mWidth = width;
            mHeight = height;
            mFormat = format;
            mSemaphore = semaphore;
            mImageView = imageView;
            mActivity = activity;
            mSensorOrientation = sensorOrientation;
        }
    }

    private LinkedList<RunArguments> queue = new LinkedList<>();
    boolean mIsScanning = true;

    synchronized void post(byte[] bytes, int width, int height, int format, int sensorOrientation,
                           Semaphore semaphore, Activity activity, ImageView debugImageView) {
        RunArguments args = new RunArguments(bytes, width, height, format, sensorOrientation,
                semaphore, activity, debugImageView);
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

        Log.d("Thread", "run");

        final RunArguments args = queue.pop();
        final Bitmap bitmap = getBitmap(args.mFrameBytes, args.mWidth, args.mHeight, args.mFormat,
                args.mSensorOrientation);

        Ocr ocr = new Ocr();
        final String number = ocr.predict(bitmap, args.mActivity);
        final List<DetectedBox> boxes = ocr.digitBoxes;
        final Activity activity = args.mActivity;
        final DetectedBox expiryBox = ocr.expiryBox;
        final Expiry expiry = ocr.expiry;

        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            public void run() {
                args.mImageView.setImageBitmap(ImageUtils.drawBoxesOnImage(bitmap, boxes,
                        expiryBox));
                if (number != null && mIsScanning) {
                    Intent intent = new Intent();
                    JSONObject card = new JSONObject();
                    try {
                        card.put("number", number);
                        if (expiry != null) {
                            card.put("expiryMonth", expiry.month);
                            card.put("expiryYear", expiry.year);
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    intent.putExtra(ScanActivity.SCAN_RESULT, card.toString());
                    activity.setResult(ScanActivity.RESULT_OK, intent);
                    activity.finish();
                    mIsScanning = false;
                }
            }
        });

        args.mSemaphore.release();
    }

    @Override
    public void run() {
        while (true) {
            runModel();
        }
    }
}
