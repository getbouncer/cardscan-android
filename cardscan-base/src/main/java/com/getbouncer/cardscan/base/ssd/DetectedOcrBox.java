package com.getbouncer.cardscan.base.ssd;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import org.json.JSONObject;

public class DetectedOcrBox {
    public float xMin, yMin, xMax, yMax;
    public float confidence;
    public int label;

    public RectF rect;


    public DetectedOcrBox(float xMin, float yMin, float xMax, float yMax, float confidence,
                          int imageWidth, int imageHeight, int label) {

        this.xMin = xMin * imageWidth;
        this.xMax = xMax * imageWidth;
        this.yMin = yMin * imageHeight;
        this.yMax = yMax * imageHeight;
        this.confidence = confidence;
        this.label = label;
        this.rect = new RectF(this.xMin, this.yMin, this.xMax, this.yMax);
    }

    @NonNull
    public JSONObject toJson() {
        return new JSONObject();
    }
}
