
package com.getbouncer.cardscan.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.getbouncer.cardscan.base.ssd.ArrUtils;
import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;
import com.getbouncer.cardscan.base.ssd.OcrPriorsGen;
import com.getbouncer.cardscan.base.ssd.PredictionAPI;
import com.getbouncer.cardscan.base.ssd.PriorsGen;
import com.getbouncer.cardscan.base.ssd.Result;

import java.util.Collections;
import java.util.List;

import java.util.ArrayList;


public class SSDOcrDetect {
    private static SSDOcrModel ssdOcrModel = null;
    private static float[][] priors = null;


    public List<DetectedSSDBox> objectBoxes = new ArrayList<>();
    boolean hadUnrecoverableException = false;

    /** We don't use the following two for now */
    public static boolean USE_GPU = false;

    static boolean isInit() {
        return ssdOcrModel != null;
    }

    private String ssdOutputToPredictions(Bitmap image){
        ArrUtils arrUtils = new ArrUtils();

        float[][] k_boxes = arrUtils.rearrangeOCRArray(ssdOcrModel.outputLocations, ssdOcrModel.featureMapSizes,
                ssdOcrModel.NUM_OF_PRIORS_PER_ACTIVATION, ssdOcrModel.NUM_OF_CORDINATES);
        k_boxes = arrUtils.reshape(k_boxes, ssdOcrModel.NUM_OF_PRIORS, ssdOcrModel.NUM_OF_CORDINATES);
        k_boxes = arrUtils.convertLocationsToBoxes(k_boxes, priors,
                ssdOcrModel.CENTER_VARIANCE, ssdOcrModel.SIZE_VARIANCE);
        k_boxes = arrUtils.centerFormToCornerForm(k_boxes);
        float[][] k_scores = arrUtils.rearrangeOCRArray(ssdOcrModel.outputClasses, ssdOcrModel.featureMapSizes,
                ssdOcrModel.NUM_OF_PRIORS_PER_ACTIVATION, ssdOcrModel.NUM_OF_CLASSES);
        k_scores = arrUtils.reshape(k_scores, ssdOcrModel.NUM_OF_PRIORS, ssdOcrModel.NUM_OF_CLASSES);
        k_scores = arrUtils.softmax2D(k_scores);

        PredictionAPI predAPI = new PredictionAPI();
        Result result = predAPI.predictionAPI(k_scores, k_boxes, ssdOcrModel.PROB_THRESHOLD,
                ssdOcrModel.IOU_THRESHOLD, ssdOcrModel.CANDIDATE_SIZE, ssdOcrModel.TOP_K);
        if (result.pickedBoxProbs.size() != 0 && result.pickedLabels.size() != 0)
        {
            for (int i = 0; i < result.pickedBoxProbs.size(); ++i){
                DetectedSSDBox ssdBox = new DetectedSSDBox(
                        result.pickedBoxes.get(i)[0], result.pickedBoxes.get(i)[1],
                        result.pickedBoxes.get(i)[2], result.pickedBoxes.get(i)[3],result.pickedBoxProbs.get(i),
                        image.getWidth(), image.getHeight(),result.pickedLabels.get(i));
                objectBoxes.add(ssdBox);
            }
        }
        String numberOCR = "";
        Collections.sort(objectBoxes);
        StringBuilder num = new StringBuilder();
        for (DetectedSSDBox box : objectBoxes){
            if (box.label == 10){
                box.label = 0;
            }
            num.append(String.valueOf(box.label));
        }
        if (CreditCardUtils.luhnCheck(num.toString())){
            numberOCR = num.toString();
            Log.d("OCR Number passed", numberOCR);
        } else {
            Log.d("OCR Number failed", num.toString());
            numberOCR = null;
        }

        return numberOCR;


    }

    private String runModel(Bitmap image) {
        final long startTime = SystemClock.uptimeMillis();

        /**Run SSD Model and use the prediction API to post process
         * the model output */

        ssdOcrModel.classifyFrame(image);
        Log.e("Before SSD Post Process", String.valueOf(SystemClock.uptimeMillis() - startTime));
        String number = ssdOutputToPredictions(image);
        Log.e("After SSD Post Process", String.valueOf(SystemClock.uptimeMillis() - startTime));

        return number;
    }

    public synchronized String predict(Bitmap image, Context context) {
        final int NUM_THREADS = 4;
        try {
            boolean createdNewModel = false;

            try{
                if (ssdOcrModel == null){
                    ssdOcrModel = new SSDOcrModel(context);
                    /** Since all the frames use the same set of priors
                     * We generate these once and use for all the frame
                     */
                    if ( priors == null){
                        priors = OcrPriorsGen.combinePriors();
                    }

                }
            } catch (Error | Exception e){
                Log.e("SSD", "Couldn't load ssd", e);
            }


            try {
                return runModel(image);
            } catch (Error | Exception e) {
                Log.i("ObjectDetect", "runModel exception, retry object detection", e);
                ssdOcrModel = new SSDOcrModel(context);
                return runModel(image);
            }
        } catch (Error | Exception e) {
            Log.e("ObjectDetect", "unrecoverable exception on ObjectDetect", e);
            hadUnrecoverableException = true;
            return null;
        }
    }

}