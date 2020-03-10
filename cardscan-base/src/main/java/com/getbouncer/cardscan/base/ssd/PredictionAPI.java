package com.getbouncer.cardscan.base.ssd;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * A utitliy class that applies non-max supression to each class
 * picks out the remaining boxes, the class probabilities for classes
 * that are kept and composes all the information in one place to be returned as
 * an object.
 */
public class PredictionAPI{
    final ArrUtils arrUtils = new ArrUtils();

    @NonNull
    public Result predictionAPI(@NonNull float[][] k_scores, @NonNull float[][] k_boxes, float probThreshold, float iouThreshold,
                                int candidateSize, int topK){

        ArrayList<Float> probs;
        ArrayList<float[]> subsetBoxes;
        ArrayList<Integer> indices;

        // skip the background class

        Result res = new Result();
        for(int classIndex = 1; classIndex < k_scores[0].length; classIndex++){
            probs = new ArrayList<Float>();
            subsetBoxes = new ArrayList<float[]>();
            indices = new ArrayList<Integer>();

            for(int rowIndex = 0; rowIndex < k_scores.length; rowIndex++){
                if(k_scores[rowIndex][classIndex] > probThreshold){
                    probs.add(k_scores[rowIndex][classIndex]);
                    subsetBoxes.add(k_boxes[rowIndex]);
                }
            }
            if (probs.size() == 0){
                continue;
            }
            indices = NMS.hardNMS(subsetBoxes, probs, iouThreshold, topK, candidateSize);

            for(int Index: indices){
                res.pickedBoxProbs.add(probs.get(Index));
                res.pickedBoxes.add(subsetBoxes.get(Index));
                res.pickedLabels.add(classIndex);
            }
        }

        return res;

    }

}