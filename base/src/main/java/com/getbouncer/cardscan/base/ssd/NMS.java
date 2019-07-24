package com.getbouncer.cardscan.base.ssd;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class NMS{
    public static ArrayList<Integer> hardNMS(ArrayList<float[]> subsetBoxes, ArrayList<Float> probs, float iouThreshold,
                                             int topK, int candidateSize ){
        float iou ;
        Float[] prob = probs.toArray(new Float[probs.size()]);
        ArrayIndexComparator comparator = new ArrayIndexComparator(prob);
        Integer[] indexes = comparator.createIndexArray();
        Arrays.sort(indexes, comparator);
        ArrayList<Integer> Indexes = new ArrayList<>(Arrays.asList(indexes));
        int current = 0;
        float[] currentBox;
        ArrayList<Integer> pickedIndices = new ArrayList<Integer>();

        if(indexes.length > 200){
            System.out.println("Greater than 200");
            System.exit(1); // TODO fix this soon
        }
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