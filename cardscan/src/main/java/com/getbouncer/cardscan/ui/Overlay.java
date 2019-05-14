package com.getbouncer.cardscan.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.getbouncer.cardscan.R;

// adapted from this: https://medium.com/@rgomez/android-how-to-draw-an-overlay-with-a-transparent-hole-471af6cf3953

public class Overlay extends View {

    private RectF circleRect;
    private int radius;

    public Overlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        //In versions > 3.0 need to define layer Type
        if (android.os.Build.VERSION.SDK_INT >= 11)
        {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }
    }

    public void setCircle(RectF rect, int radius) {
        this.circleRect = rect;
        this.radius = radius;
        //Redraw after defining circle
        postInvalidate();
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(circleRect != null) {
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(getResources().getColor(R.color.camera_background));
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPaint(paint);

            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawRoundRect(circleRect, radius, radius, paint);
        }
    }
}