package com.getbouncer.cardscan.base.ssd;

import java.util.ArrayList;

public class Result {
    /** This class is used to encapsulate the resutls from the
     * SSD prediction API. pickedBoxes is the picked bounding
     * boxes that pass through the non-max supression as well
     * as the confidence thresholds
     * pickedBoxProbs corresponds to the probabilities associated
     * with the picked boxes. pickedLabels is the classes associated
     * with the pickedBoxes.
     */

    public ArrayList<Float> pickedBoxProbs;
    public ArrayList<Integer> pickedLabels;
    public ArrayList<float[]> pickedBoxes;

    public Result(){
        this.pickedBoxProbs = new ArrayList<Float>();
        this.pickedLabels = new ArrayList<Integer>();
        this.pickedBoxes = new ArrayList<float[]>();
    }
}
