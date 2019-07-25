package com.getbouncer.cardscan.base.ssd;

import java.util.ArrayList;

public class PredictionAPI{
    ArrayList<Float> pickedBoxProbs;
    ArrayList<Integer> pickedLabels;
    ArrayList<float[]> pickedBoxes;

    ArrUtils arrUtils = new ArrUtils();

    public Result predictionAPI(float[][] k_scores, float[][] k_boxes, float probThreshold, float iouThreshold,
                                int candidateSize, int topK){

        /**
         * A utitliy class that applies non-max supression to each class
         * picks out the remaining boxes, the class probabilities for classes
         * that are kept and composes all the information in one place to be returned as
         * an object.
         */
        pickedBoxProbs = new ArrayList<Float>();
        pickedLabels = new ArrayList<Integer>();
        pickedBoxes = new ArrayList<float[]>();

        ArrayList<Float> probs;
        ArrayList<float[]> subsetBoxes;
        ArrayList<Integer> indices;

        // skip the background class

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
                pickedBoxProbs.add(probs.get(Index));
                pickedBoxes.add(subsetBoxes.get(Index));
                pickedLabels.add(classIndex);
            }
        }
        Result res = new Result();
        res.pickedBoxProbs = pickedBoxProbs;
        res.pickedBoxes = pickedBoxes;
        res.pickedLabels = pickedLabels;

        return res;

    }

}