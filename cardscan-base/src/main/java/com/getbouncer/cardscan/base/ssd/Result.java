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

    public final ArrayList<Float> pickedBoxProbs = new ArrayList<Float>();
    public final ArrayList<Integer> pickedLabels = new ArrayList<Integer>();
    public final ArrayList<float[]> pickedBoxes = new ArrayList<float[]>();
}
