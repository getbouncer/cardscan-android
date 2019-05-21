package com.getbouncer.cardscan.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import com.getbouncer.cardscan.R;

public class OverlayWhite extends Overlay {

    int backgroundColorId = R.color.white_background;
    int cornerColorId = R.color.gray;

    public OverlayWhite(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setColorIds(int backgroundColorId, int cornerColorId) {
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
