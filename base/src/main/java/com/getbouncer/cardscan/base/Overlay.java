package com.getbouncer.cardscan.base;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Xfermode;
import android.util.AttributeSet;
import android.view.View;

// adapted from this: https://medium.com/@rgomez/android-how-to-draw-an-overlay-with-a-transparent-hole-471af6cf3953

class Overlay extends View {

    private RectF rect;
    private RectF oval = new RectF();
    private int radius;
    private final Xfermode xfermode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);

    int cornerDp = 6;

    //private Paint paintAntiAlias = new Paint(Paint.ANTI_ALIAS_FLAG);
    //private Paint paint = new Paint();

    public Overlay(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    protected int getBackgroundColorId() {
        return R.color.camera_background;
    }

    protected int getCornerColorId() {
        return R.color.ios_green;
    }

    public void setCircle(RectF rect, int radius) {
        this.rect = rect;
        this.radius = radius;
        postInvalidate();
    }

    private int dpToPx(int dp) {
        return (int) (dp * Resources.getSystem().getDisplayMetrics().density);
    }

    @Override
    public void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if(rect != null) {
            Paint paintAntiAlias = new Paint(Paint.ANTI_ALIAS_FLAG);
            paintAntiAlias.setColor(getResources().getColor(getBackgroundColorId()));
            paintAntiAlias.setStyle(Paint.Style.FILL);
            canvas.drawPaint(paintAntiAlias);

            paintAntiAlias.setXfermode(xfermode);
            canvas.drawRoundRect(rect, radius, radius, paintAntiAlias);

            Paint paint = new Paint();
            paint.setColor(getResources().getColor(getCornerColorId()));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpToPx(cornerDp));

            // top left
            int lineLength = dpToPx(20);
            float x = rect.left - dpToPx(1);
            float y = rect.top - dpToPx(1);
            oval.left = x;
            oval.top = y;
            oval.right = x + 2*radius;
            oval.bottom = y + 2*radius;
            canvas.drawArc(oval, 180, 90, false, paint);
            canvas.drawLine(oval.left, oval.bottom - radius, oval.left,
                    oval.bottom - radius + lineLength, paint);
            canvas.drawLine(oval.right - radius, oval.top,
                    oval.right - radius + lineLength, oval.top, paint);

            // top right
            x = rect.right + dpToPx(1) - 2*radius;
            y = rect.top - dpToPx(1);
            oval.left = x;
            oval.top = y;
            oval.right = x + 2*radius;
            oval.bottom = y + 2*radius;
            canvas.drawArc(oval, 270, 90, false, paint);
            canvas.drawLine(oval.right, oval.bottom - radius, oval.right,
                    oval.bottom - radius + lineLength, paint);
            canvas.drawLine(oval.right - radius, oval.top,
                    oval.right - radius - lineLength, oval.top, paint);

            // bottom right
            x = rect.right + dpToPx(1) - 2*radius;
            y = rect.bottom + dpToPx(1) - 2*radius;
            oval.left = x;
            oval.top = y;
            oval.right = x + 2*radius;
            oval.bottom = y + 2*radius;
            canvas.drawArc(oval, 0, 90, false, paint);
            canvas.drawLine(oval.right, oval.bottom - radius, oval.right,
                    oval.bottom - radius - lineLength, paint);
            canvas.drawLine(oval.right - radius, oval.bottom,
                    oval.right - radius - lineLength, oval.bottom, paint);

            // bottom left
            x = rect.left - dpToPx(1);
            y = rect.bottom + dpToPx(1) - 2*radius;
            oval.left = x;
            oval.top = y;
            oval.right = x + 2*radius;
            oval.bottom = y + 2*radius;
            canvas.drawArc(oval, 90, 90, false, paint);
            canvas.drawLine(oval.left, oval.bottom - radius, oval.left,
                    oval.bottom - radius - lineLength, paint);
            canvas.drawLine(oval.right - radius, oval.bottom,
                    oval.right - radius + lineLength, oval.bottom, paint);
        }
    }
}