package com.getbouncer.cardscan.base.ssd;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import org.json.JSONObject;


public class DetectedOcrBox implements Comparable<DetectedOcrBox> {
    public float XMin, YMin, XMax, YMax;
    public float confidence;
    public int label;

    public RectF rect;


    public DetectedOcrBox(float XMin, float YMin, float XMax, float YMax, float confidence,
                          int ImageWidth, int ImageHeight, int label) {

        this.XMin = XMin * ImageWidth;
        this.XMax = XMax * ImageWidth;
        this.YMin = YMin * ImageHeight;
        this.YMax = YMax * ImageHeight;
        this.confidence = confidence;
        this.label = label;
        this.rect = new RectF(this.XMin, this.YMin, this.XMax, this.YMax);
    }

    @NonNull
    public JSONObject toJson() {
        return new JSONObject();
    }

    @Override
    public int compareTo(@NonNull DetectedOcrBox detectedOcrBox) {
        return Float.compare(this.XMin, detectedOcrBox.XMin);
    }
}
