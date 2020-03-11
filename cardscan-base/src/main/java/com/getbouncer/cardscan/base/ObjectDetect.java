package com.getbouncer.cardscan.base;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.getbouncer.cardscan.base.ssd.DetectedSSDBox;
import com.getbouncer.cardscan.base.ssd.DetectionBox;
import com.getbouncer.cardscan.base.ssd.ObjectPriorsGen;
import com.getbouncer.cardscan.base.ssd.SSD;
import com.getbouncer.cardscan.base.ssd.domain.ClassifierScores;
import com.getbouncer.cardscan.base.ssd.domain.SizeAndCenter;
import com.getbouncer.cardscan.base.util.ArrayExtensions;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.List;

import java.util.ArrayList;

import kotlin.jvm.functions.Function1;


public class ObjectDetect {
    @Nullable private static SSDDetect ssdDetect = null;
    @Nullable private static float[][] priors = null;

    @NonNull private final File ssdModelFile;

    @NonNull public List<DetectedSSDBox> objectBoxes = new ArrayList<>();
    boolean hadUnrecoverableException = false;

    /** We don't use the following two for now */
    public static boolean USE_GPU = false;

    static boolean isInit() {
        return ssdDetect != null;
    }

    public ObjectDetect(@NotNull File modelFile) {
        this.ssdModelFile = modelFile;
    }

    private void ssdOutputToPredictions(@NonNull Bitmap image) {
        if (ssdDetect == null || priors == null) {
            return;
        }

        float[][] k_boxes = SSD.rearrangeArray(ssdDetect.outputLocations, SSDDetect.featureMapSizes,
                SSDDetect.NUM_OF_PRIORS_PER_ACTIVATION, SSDDetect.NUM_OF_CORDINATES);
        k_boxes = ArrayExtensions.reshape(k_boxes, SSDDetect.NUM_OF_CORDINATES);
        SizeAndCenter.adjustLocations(k_boxes, priors, SSDDetect.CENTER_VARIANCE, SSDDetect.SIZE_VARIANCE);
        SizeAndCenter.toRectForm(k_boxes);

        float[][] k_scores = SSD.rearrangeArray(ssdDetect.outputClasses, SSDDetect.featureMapSizes,
                SSDDetect.NUM_OF_PRIORS_PER_ACTIVATION, SSDDetect.NUM_OF_CLASSES);
        k_scores = ArrayExtensions.reshape(k_scores, SSDDetect.NUM_OF_CLASSES);
        ClassifierScores.softMax2D(k_scores);

        List<DetectionBox> detectionBoxes = SSD.extractPredictions(
            k_scores,
            k_boxes,
            new Size(image.getWidth(), image.getHeight()),
            SSDDetect.PROB_THRESHOLD,
            SSDDetect.IOU_THRESHOLD,
            SSDDetect.TOP_K,
            new Function1<Integer, Integer>() {
                @Override
                public Integer invoke(Integer integer) {
                    return integer;
                }
            }
        );

        for (DetectionBox detectionBox : detectionBoxes) {
            objectBoxes.add(new DetectedSSDBox(
                    detectionBox.getRect().left,
                    detectionBox.getRect().top,
                    detectionBox.getRect().right,
                    detectionBox.getRect().bottom,
                    detectionBox.getConfidence(),
                    detectionBox.getImageSize().getWidth(),
                    detectionBox.getImageSize().getHeight(),
                    detectionBox.getLabel()
            ));
        }
    }

    @NonNull
    private String runModel(@NonNull Bitmap image) {
        final long startTime = SystemClock.uptimeMillis();

        /*
         * Run SSD Model and use the prediction API to post process
         * the model output
         */

        if (ssdDetect == null) {
            return "Failure";
        }

        ssdDetect.classifyFrame(image);
        if (GlobalConfig.PRINT_TIMING) {
            Log.d("Before SSD Post Process", String.valueOf(SystemClock.uptimeMillis() - startTime));
        }
        ssdOutputToPredictions(image);
        if (GlobalConfig.PRINT_TIMING) {
            Log.d("After SSD Post Process", String.valueOf(SystemClock.uptimeMillis() - startTime));
        }

        return "Success";
    }

    @Nullable
    public synchronized String predictOnCpu(@NonNull Bitmap image, @NonNull Context context) {
        final int NUM_THREADS = 4;
        try {
            boolean createdNewModel = false;

            try{
                if (ssdDetect == null){
                    ssdDetect = new SSDDetect(context, ssdModelFile);
                    ssdDetect.setNumThreads(NUM_THREADS);
                    /*
                     * Since all the frames use the same set of priors
                     * We generate these once and use for all the frame
                     */
                    if ( priors == null){
                        priors = ObjectPriorsGen.combinePriors();
                    }

                }
            } catch (Error | Exception e){
                Log.e("SSD", "Couldn't load ssd", e);
            }


            try {
                return runModel(image);
            } catch (Error | Exception e) {
                Log.i("ObjectDetect", "runModel exception, retry object detection", e);
                ssdDetect = new SSDDetect(context, ssdModelFile);
                return runModel(image);
            }
        } catch (Error | Exception e) {
            Log.e("ObjectDetect", "unrecoverable exception on ObjectDetect", e);
            hadUnrecoverableException = true;
            return null;
        }
    }
}
