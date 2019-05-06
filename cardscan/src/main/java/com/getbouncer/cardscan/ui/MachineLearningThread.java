package com.getbouncer.cardscan.ui;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.Image;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ImageView;

import com.getbouncer.cardscan.CreditCard;
import com.getbouncer.cardscan.DetectedBox;
import com.getbouncer.cardscan.Expiry;
import com.getbouncer.cardscan.ImageUtils;
import com.getbouncer.cardscan.Ocr;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Semaphore;

public class MachineLearningThread implements Runnable {

    class RunArguments {
        private final Image mImage;
        private final Semaphore mSemaphore;
        private final ImageView mImageView;
        private final int mSensorOrientation;
        private final Activity mActivity;

        RunArguments(Image image, Semaphore semaphore, ImageView imageView,
                            int sensorOrientation, Activity activity) {
            mImage = image;
            mSemaphore = semaphore;
            mImageView = imageView;
            mSensorOrientation = sensorOrientation;
            mActivity = activity;
        }

    }

    private LinkedList<RunArguments> queue = new LinkedList<>();
    boolean mIsScanning = true;

    synchronized void post(Image image, Semaphore semaphore, ImageView imageView,
                                  int sensorOrientation, Activity activity) {
        Log.d("Thread", "post");
        RunArguments args = new RunArguments(image, semaphore, imageView, sensorOrientation,
                activity);
        queue.push(args);
        notify();
    }

    private Bitmap getBitmap(Image image, int sensorOrientation) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] imageBytes = new byte[buffer.remaining()];
        buffer.get(imageBytes);
        Bitmap b = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        Matrix matrix = new Matrix();
        matrix.postRotate(sensorOrientation);
        Bitmap bm = Bitmap.createBitmap(b, 0, 0, b.getWidth(), b.getHeight(),
                matrix, true);
        double width = bm.getWidth();
        double height = 302.0 * width / 480.0;
        int x = 0;
        int y = (int) Math.round(((double) bm.getHeight()) * 0.5 - height * 0.5);
        return Bitmap.createBitmap(bm, x, y, (int) width, (int) height);
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
        final Bitmap bitmap = getBitmap(args.mImage, args.mSensorOrientation);

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

        args.mImage.close();
        args.mSemaphore.release();
    }

    @Override
    public void run() {
        while (true) {
            runModel();
        }
    }
}
