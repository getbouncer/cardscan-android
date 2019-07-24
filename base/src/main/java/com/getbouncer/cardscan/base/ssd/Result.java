package com.getbouncer.cardscan.base.ssd;

import java.util.ArrayList;

public class Result {

    public ArrayList<Float> pickedBoxProbs;
    public ArrayList<Integer> pickedLabels;
    public ArrayList<float[]> pickedBoxes;

    public Result(){
        this.pickedBoxProbs = new ArrayList<Float>();
        this.pickedLabels = new ArrayList<Integer>();
        this.pickedBoxes = new ArrayList<float[]>();
    }
}
