package com.getbouncer.cardscan.base.ssd;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class NMS{
    @NonNull
    public static ArrayList<Integer> hardNMS(@NonNull ArrayList<float[]> subsetBoxes, @NonNull ArrayList<Float> probs, float iouThreshold,
                                             int topK, int candidateSize ){

        /** In this project we implement HARD NMS and NOT Soft NMS
         * I highly recommend checkout SOFT NMS Implementation of Facebook Detectron Framework
         *
         *  Args:
         *  subsetBoxes (N, 4): boxes in corner-form and probabilities.
         *  iouThreshold: intersection over union threshold.
         *  topK: keep top_k results. If k <= 0, keep all the results.
         *  candidateSize: only consider the candidates with the highest scores.
         *
         *  Returns:
         *  pickedIndices: a list of indexes of the kept boxes
         */

        float iou ;
        Float[] prob = probs.toArray(new Float[probs.size()]);
        ArrayIndexComparator comparator = new ArrayIndexComparator(prob);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);

        if(indexes.length > 200){  // Exceptional Situation
            indexes = Arrays.copyOfRange(indexes, 0, 200);
        }
        ArrayList<Integer> Indexes = new ArrayList<>(Arrays.asList(indexes));

        int current = 0;
        float[] currentBox;
        ArrayList<Integer> pickedIndices = new ArrayList<Integer>();


        while(Indexes.size() > 0){
            current = Indexes.get(0);
            pickedIndices.add(current);

            if (topK > 0 && topK == pickedIndices.size() || Indexes.size() == 1){
                break;
            }
            currentBox = subsetBoxes.get(current);
            Indexes.remove(0);

            Iterator<Integer> it = Indexes.iterator();
            while(it.hasNext()){
                Integer Index = it.next();
                iou = NMSUtil.IOUOf(currentBox, subsetBoxes.get(Index));
                if(iou >= iouThreshold){
                    it.remove();

                }
            }
        }
        return pickedIndices;
    }


}