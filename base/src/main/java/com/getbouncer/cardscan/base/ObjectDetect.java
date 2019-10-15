package com.getbouncer.cardscan.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;

import com.getbouncer.cardscan.base.ssd.ArrUtils;
import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;
import com.getbouncer.cardscan.base.ssd.PredictionAPI;
import com.getbouncer.cardscan.base.ssd.PriorsGen;
import com.getbouncer.cardscan.base.ssd.Result;

import java.util.List;

import java.util.ArrayList;


public class ObjectDetect {
    private static SSDDetect ssdDetect = null;
    private static float[][] priors = null;


    public List<DetectedSSDBox> objectBoxes = new ArrayList<>();
    boolean hadUnrecoverableException = false;

    /** We don't use the following two for now */
    public static boolean USE_GPU = false;

    static boolean isInit() {
        return ssdDetect != null;
    }

    private void ssdOutputToPredictions(Bitmap image){
        ArrUtils arrUtils = new ArrUtils();

        float[][] k_boxes = arrUtils.rearrangeArray(ssdDetect.outputLocations, ssdDetect.featureMapSizes,
                                ssdDetect.NUM_OF_PRIORS_PER_ACTIVATION, ssdDetect.NUM_OF_CORDINATES);
        k_boxes = arrUtils.reshape(k_boxes, ssdDetect.NUM_OF_PRIORS, ssdDetect.NUM_OF_CORDINATES);
        k_boxes = arrUtils.convertLocationsToBoxes(k_boxes, priors,
                                ssdDetect.CENTER_VARIANCE, ssdDetect.SIZE_VARIANCE);
        k_boxes = arrUtils.centerFormToCornerForm(k_boxes);
        float[][] k_scores = arrUtils.rearrangeArray(ssdDetect.outputClasses, ssdDetect.featureMapSizes,
                      ssdDetect.NUM_OF_PRIORS_PER_ACTIVATION, ssdDetect.NUM_OF_CLASSES);
        k_scores = arrUtils.reshape(k_scores, ssdDetect.NUM_OF_PRIORS, ssdDetect.NUM_OF_CLASSES);
        k_scores = arrUtils.softmax2D(k_scores);

        PredictionAPI predAPI = new PredictionAPI();
        Result result = predAPI.predictionAPI(k_scores, k_boxes, ssdDetect.PROB_THRESHOLD,
                      ssdDetect.IOU_THRESHOLD, ssdDetect.CANDIDATE_SIZE, ssdDetect.TOP_K);
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


    }

    private String runModel(Bitmap image) {
        final long startTime = SystemClock.uptimeMillis();

        /**Run SSD Model and use the prediction API to post process
         * the model output */

        ssdDetect.classifyFrame(image);
        Log.e("Before SSD Post Process", String.valueOf(SystemClock.uptimeMillis() - startTime));
        ssdOutputToPredictions(image);
        Log.e("After SSD Post Process", String.valueOf(SystemClock.uptimeMillis() - startTime));

        return "Success";
    }

    public synchronized String predict(Bitmap image, Context context) {
        final int NUM_THREADS = 4;
        try {
            boolean createdNewModel = false;

            try{
                if (ssdDetect == null){
                    ssdDetect = new SSDDetect(context);
                    /** Since all the frames use the same set of priors
                     * We generate these once and use for all the frame
                     */
                    if ( priors == null){
                        priors = PriorsGen.combinePriors();
                    }

                }
            } catch (Error | Exception e){
                Log.e("SSD", "Couldn't load ssd", e);
            }


            try {
                return runModel(image);
            } catch (Error | Exception e) {
                Log.i("ObjectDetect", "runModel exception, retry object detection", e);
                ssdDetect = new SSDDetect(context);
                return runModel(image);
            }
        } catch (Error | Exception e) {
            Log.e("ObjectDetect", "unrecoverable exception on ObjectDetect", e);
            hadUnrecoverableException = true;
            return null;
        }
    }

    }
