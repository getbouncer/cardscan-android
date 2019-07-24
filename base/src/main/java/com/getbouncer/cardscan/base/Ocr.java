package com.getbouncer.cardscan.base;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.graphics.Bitmap;
import android.util.Log;

import com.getbouncer.cardscan.base.ssd.ArrUtils;
import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;
import com.getbouncer.cardscan.base.ssd.PredictionAPI;
import com.getbouncer.cardscan.base.ssd.PriorsGen;
import com.getbouncer.cardscan.base.ssd.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * This class is not thread safe, make sure that all methods run on the same thread.
 */
public class Ocr {
    private static FindFourModel findFour = null;
    private static RecognizedDigitsModel recognizedDigitsModel = null;
    private static SSDDetect ssdDetect = null;
    public List<DetectedBox> digitBoxes = new ArrayList<>();
    public DetectedBox expiryBox = null;
    public Expiry expiry = null;
    public List<DetectedSSDBox> objectBoxes = new ArrayList<>();
    boolean hadUnrecoverableException = false;
    public static boolean USE_GPU = false;

    static boolean isInit() {
        return findFour != null && recognizedDigitsModel != null && ssdDetect != null;
    }

    private ArrayList<DetectedBox> detectBoxes(Bitmap image) {
        ArrayList<DetectedBox> boxes = new ArrayList<>();
        for (int row = 0; row < findFour.rows; row++) {
            for (int col = 0; col < findFour.cols; col++) {

                if (findFour.hasDigits(row, col)) {
                    float confidence = findFour.digitConfidence(row, col);
                    CGSize imageSize = new CGSize(image.getWidth(), image.getHeight());
                    DetectedBox box = new DetectedBox(row, col, confidence, findFour.rows,
                            findFour.cols, findFour.boxSize, findFour.cardSize, imageSize);
                    boxes.add(box);
                }

            }
        }
        return boxes;
    }

    private ArrayList<DetectedBox> detectExpiry(Bitmap image) {
        ArrayList<DetectedBox> boxes = new ArrayList<>();
        for (int row = 0; row < findFour.rows; row++) {
            for (int col = 0; col < findFour.cols; col++) {

                if (findFour.hasExpiry(row, col)) {
                    float confidence = findFour.expiryConfidence(row, col);
                    CGSize imageSize = new CGSize(image.getWidth(), image.getHeight());
                    DetectedBox box = new DetectedBox(row, col, confidence, findFour.rows,
                            findFour.cols, findFour.boxSize, findFour.cardSize, imageSize);
                    boxes.add(box);
                }

            }
        }
        return boxes;
    }

    private Result ssdOutputToPredictions(Bitmap image){
        ArrUtils arrUtils = new ArrUtils();
        int[] featureMapSizes = {19, 10};
        float[][] priorsCombined = PriorsGen.combinePriors();

        float[][] k_boxes = arrUtils.rearrangeArray(ssdDetect.outputLocations, featureMapSizes, 6, ssdDetect.NUM_OF_CORDINATES);
        k_boxes = arrUtils.reshape(k_boxes, ssdDetect.NUM_OF_PRIORS, ssdDetect.NUM_OF_CORDINATES);
        k_boxes = arrUtils.convertLocationsToBoxes(k_boxes, priorsCombined, 0.1f, 0.2f);
        k_boxes = arrUtils.centerFormToCornerForm(k_boxes);
        float[][] k_scores = arrUtils.rearrangeArray(ssdDetect.outputClasses, featureMapSizes, 6, ssdDetect.NUM_OF_CLASSES);
        k_scores = arrUtils.reshape(k_scores, ssdDetect.NUM_OF_PRIORS, ssdDetect.NUM_OF_CLASSES);
        k_scores = arrUtils.softmax2D(k_scores);
        float probThreshold = 0.2f;
        float iouThreshold = 0.45f;
        int candidateSize = 200;
        int topK = 10;
        PredictionAPI predAPI = new PredictionAPI();
        Result result = predAPI.predictionAPI(k_scores, k_boxes, probThreshold, iouThreshold, candidateSize, topK);
        if (result.pickedBoxProbs.size() != 0 && result.pickedLabels.size() != 0)
        {
            for (int i = 0; i < result.pickedBoxProbs.size(); ++i){
                DetectedSSDBox ssdBox = new DetectedSSDBox(
                        result.pickedBoxes.get(i)[0], result.pickedBoxes.get(i)[1],
                        result.pickedBoxes.get(i)[2], result.pickedBoxes.get(i)[3],result.pickedBoxProbs.get(i),
                        image.getWidth(), image.getHeight(),"Test");
                objectBoxes.add(ssdBox);
            }
        }

        return result;


    }

    private String runModel(Bitmap image) {
        findFour.classifyFrame(image);
        // Run SSD Model
        ssdDetect.classifyFrame(image);
        // pass the output through the prediction API
        Result result = ssdOutputToPredictions(image);
        Log.e("After SSD Post Process", String.valueOf(result.pickedLabels) + image.getWidth());

        ArrayList<DetectedBox> boxes = detectBoxes(image);
        ArrayList<DetectedBox> expiryBoxes = detectExpiry(image);
        PostDetectionAlgorithm postDetection = new PostDetectionAlgorithm(boxes, findFour);
        RecognizeNumbers recognizeNumbers = new RecognizeNumbers(image, findFour.rows,
                findFour.cols);
        ArrayList<ArrayList<DetectedBox>> lines = postDetection.horizontalNumbers();

        String algorithm = null;
        String number = recognizeNumbers.number(recognizedDigitsModel, lines);
        if (number == null) {
            ArrayList<ArrayList<DetectedBox>> verticalLines = postDetection.verticalNumbers();
            number = recognizeNumbers.number(recognizedDigitsModel, verticalLines);
            lines.addAll(verticalLines);
        } else {
            algorithm = "horizontal";
        }

        if (number == null) {
            ArrayList<ArrayList<DetectedBox>> amexLines = postDetection.amexNumbers();
            number = recognizeNumbers.amexNumber(recognizedDigitsModel, amexLines);
            lines.addAll(amexLines);
            if (number != null) {
                algorithm = "amex";
            }
        } else {
            algorithm = "vertical";
        }

        boxes = new ArrayList<>();
        for (ArrayList<DetectedBox> numbers:lines) {
            boxes.addAll(numbers);
        }

        this.expiry = null;
        if (expiryBoxes.size() > 0) {
            Collections.sort(expiryBoxes);
            DetectedBox expiryBox = expiryBoxes.get(expiryBoxes.size() - 1);
            this.expiry = Expiry.from(recognizedDigitsModel, image, expiryBox.rect);
            if (this.expiry != null) {
                this.expiryBox = expiryBox;
            } else {
                this.expiryBox = null;
            }
        }


        this.digitBoxes = boxes;
        return number;
    }

    private boolean hasOpenGl31(Context context) {
        int openGlVersion = 0x00030001;
        ActivityManager activityManager =
                (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configInfo = activityManager.getDeviceConfigurationInfo();
        if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
            return configInfo.reqGlEsVersion >= openGlVersion;
        } else {
            return false;
        }
    }

    public synchronized String predict(Bitmap image, Context context) {
        final int NUM_THREADS = 4;
        try {
            boolean createdNewModel = false;

            if (findFour == null) {
                findFour = new FindFourModel(context);
                findFour.setNumThreads(NUM_THREADS);
                createdNewModel = true;
            }

            if (recognizedDigitsModel == null) {
                recognizedDigitsModel = new RecognizedDigitsModel(context);
                recognizedDigitsModel.setNumThreads(NUM_THREADS);
                createdNewModel = true;
            }

            if (createdNewModel && hasOpenGl31(context) && USE_GPU) {
                try {
                    findFour.useGpu();
                    recognizedDigitsModel.useGpu();
                } catch (Error | Exception e) {
                    Log.i("Ocr", "useGpu exception, falling back to CPU", e);
                    findFour = new FindFourModel(context);
                    recognizedDigitsModel = new RecognizedDigitsModel(context);
                }
            }


            try{
                if (ssdDetect == null){
                ssdDetect = new SSDDetect(context);
                }
            } catch (Error | Exception e){
                Log.e("SSD", "Couldn't load ssd", e);
            }


            try {
                return runModel(image);
            } catch (Error | Exception e) {
                Log.i("Ocr", "runModel exception, retry prediction", e);
                findFour = new FindFourModel(context);
                recognizedDigitsModel = new RecognizedDigitsModel(context);
                return runModel(image);
            }
        } catch (Error | Exception e) {
            Log.e("Ocr", "unrecoverable exception on Ocr", e);
            hadUnrecoverableException = true;
            return null;
        }
    }
}
