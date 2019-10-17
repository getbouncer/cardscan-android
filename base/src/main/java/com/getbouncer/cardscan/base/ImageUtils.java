package com.getbouncer.cardscan.base;


import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.Log;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;

import java.util.List;

public class ImageUtils {

    public static Bitmap drawBoxesOnImage(Bitmap frame, List<DetectedBox> boxes,
                                          DetectedBox expiryBox) {
        Paint paint = new Paint(0);
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);

        Bitmap mutableBitmap = frame.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        for (DetectedBox box:boxes) {
            canvas.drawRect(box.rect.RectF(), paint);
        }

        paint.setColor(Color.RED);
        if (expiryBox != null) {
            canvas.drawRect(expiryBox.rect.RectF(), paint);
        }

        return mutableBitmap;
    }

    public static Bitmap drawBoxesOnImage(Bitmap frame, List<DetectedSSDBox> boxes) {
        Paint paint = new Paint(0);
        paint.setColor(Color.RED);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3);

        Bitmap mutableBitmap = frame.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(mutableBitmap);
        for (DetectedSSDBox box:boxes) {

            if (box.label == 3) {
                paint.setColor(Color.RED);
                Log.d("ImageUtils", "Confidence for card -> " + box.confidence);
                canvas.drawRect(box.rect, paint);
            } else {
                paint.setColor(Color.GREEN);
                canvas.drawRect(box.rect, paint);
            }
        }

        return mutableBitmap;
    }
}
