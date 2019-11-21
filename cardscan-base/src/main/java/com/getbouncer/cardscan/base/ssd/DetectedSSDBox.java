
package com.getbouncer.cardscan.base.ssd;

import android.graphics.RectF;

import org.json.JSONObject;


public class DetectedSSDBox implements Comparable<DetectedSSDBox> {
    public float XMin, YMin, XMax, YMax;
    public float confidence;
    public int label;

    public RectF rect;


    public DetectedSSDBox(float XMin, float YMin, float XMax, float YMax, float confidence,
                          int ImageWidth, int ImageHeight, int label) {

        this.XMin = XMin * ImageWidth;
        this.XMax = XMax * ImageWidth;
        this.YMin = YMin * ImageHeight;
        this.YMax = YMax * ImageHeight;
        this.confidence = confidence;
        this.label = label;
        this.rect = new RectF(this.XMin, this.YMin, this.XMax, this.YMax);
    }

    public JSONObject toJson() {
        return new JSONObject();
    }

    @Override
    public int compareTo(DetectedSSDBox detectedSSDBox) {
        return Float.compare(this.XMin, detectedSSDBox.XMin);
    }
}
