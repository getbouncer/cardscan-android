package com.getbouncer.cardscan.base.ssd;

import android.graphics.RectF;

import org.json.JSONException;
import org.json.JSONObject;


public class DetectedSSDBox {
    float XMin, YMin, XMax, YMax;
    public float confidence;
    public int label;
    int imageWidth, imageHeight;

    public RectF rect;


    public DetectedSSDBox(float XMin, float YMin, float XMax, float YMax, float confidence,
                          int imageWidth, int imageHeight, int label) {

        this.XMin = XMin * imageWidth;
        this.XMax = XMax * imageWidth;
        this.YMin = YMin * imageHeight;
        this.YMax = YMax * imageHeight;
        this.confidence = confidence;
        this.label = label;
        this.imageWidth = imageWidth;
        this.imageHeight = imageHeight;
        this.rect = new RectF(this.XMin, this.YMin, this.XMax, this.YMax);
    }

    public JSONObject toJson() {
        try {
            JSONObject result = new JSONObject();
            result.put("x_min", this.XMin / imageWidth);
            result.put("y_min", this.YMin / imageHeight);
            result.put("width", (this.XMax - this.XMin) / imageWidth);
            result.put("height", (this.YMax - this.YMin) / imageHeight);
            result.put("label", this.label);
            result.put("confidence", this.confidence);
            return result;
        } catch (JSONException je) {
            je.printStackTrace();
            return new JSONObject();
        }
    }

    /**
     * 0.0.3 of the model has an off-by-one issue w/ the label
     */
    public JSONObject toJsonV3() {
        try {
            JSONObject result = new JSONObject();
            result.put("x_min", this.XMin / imageWidth);
            result.put("y_min", this.YMin / imageHeight);
            result.put("width", (this.XMax - this.XMin) / imageWidth);
            result.put("height", (this.YMax - this.YMin) / imageHeight);
            result.put("label", this.label - 1);
            result.put("confidence", this.confidence);
            return result;
        } catch (JSONException je) {
            je.printStackTrace();
            return new JSONObject();
        }
    }
}

