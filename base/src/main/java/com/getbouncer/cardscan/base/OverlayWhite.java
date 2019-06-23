package com.getbouncer.cardscan.base;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

public class OverlayWhite extends Overlay {

    int backgroundColorId = R.color.white_background;
    int cornerColorId = R.color.gray;

    public OverlayWhite(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        cornerDp = 3;
    }

    public void setColorIds(int backgroundColorId, int cornerColorId) {
        this.backgroundColorId = backgroundColorId;
        this.cornerColorId = cornerColorId;
        postInvalidate();
    }

    @Override
    protected int getBackgroundColorId() {
        return backgroundColorId;
    }

    @Override
    protected int getCornerColorId() {
        return cornerColorId;
    }
}
