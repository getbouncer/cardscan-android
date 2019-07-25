package com.getbouncer.cardscan.base.ssd;


import java.util.ArrayList;
import java.util.Arrays;

public class ArrUtils {

    /** Basic Matrix handling utilities needed for SSD Framework
     */

    public float[][] reshape(float[][] nums, int r, int c) {
        int totalElements = nums.length * nums[0].length;
        if (totalElements != r * c || totalElements % r != 0) {
            return nums;
        }
        final float[][] result = new float[r][c];
        int newR = 0;
        int newC = 0;
        for (int i = 0; i < nums.length; i++) {
            for (int j = 0; j < nums[i].length; j++) {
                result[newR][newC] = nums[i][j];
                newC++;
                if (newC == c) {
                    newC = 0;
                    newR++;
                }
            }
        }
        return result;
    }


    public float[][] rearrangeLayer(float[][] locations, int steps, int noOfPriors, int locationsPerPrior){
        /** The model outputs a particular location or a particular class of each prior before moving on to the
         * next prior. For instance, the model will output probabilities for background class corresponding
         * to all priors before outputting the probability of next class for the first prior.
         * This method serves to rearrange the output if you are using outputs from a single layer.
         * If you use outputs from multiple layers use the next method defined below
         */
        int totalNumberOfLocationsForAllPriors = steps * steps * noOfPriors * locationsPerPrior;
        float[][] rearranged = new float[1][totalNumberOfLocationsForAllPriors];
        int stepsForLoop = steps - 1;
        int i = 0;
        int j = 0;
        int step = 0;

        while (i < totalNumberOfLocationsForAllPriors){
            while (step < steps){
                j = step;
                while (j < totalNumberOfLocationsForAllPriors - stepsForLoop + step){
                    rearranged[0][i] = locations[0][j];
                    i++;
                    j = j + steps;
                }
                step++;
            }
        }
        return rearranged;
    }

    public float[][] rearrangeArray(float[][] locations, int[] featureMapSizes,
                                     int noOfPriors, int locationsPerPrior){
        /** The model outputs a particular location or a particular class of each prior before moving on to the
         * next prior. For instance, the model will output probabilities for background class corresponding
         * to all priors before outputting the probability of next class for the first prior.
         * This method serves to rearrange the output if you are using outputs from multiple layers
         * If you use outputs from single layer use the method defined above
         */

        int totalLocationsForAllLayers = 0;

        for (int size : featureMapSizes){
            totalLocationsForAllLayers = totalLocationsForAllLayers + size*size*noOfPriors*locationsPerPrior;
        }

        float[][] rearranged = new float[1][totalLocationsForAllLayers];
        int offset = 0;
        for (int steps : featureMapSizes){
            int totalNumberOfLocationsForThisLayer = steps * steps * noOfPriors * locationsPerPrior;
            int stepsForLoop = steps - 1;
            int j = 0;
            int i = 0;
            int step = 0;

            while (i < totalNumberOfLocationsForThisLayer){
                while (step < steps){
                    j = step;
                    while (j < totalNumberOfLocationsForThisLayer - stepsForLoop + step){
                        rearranged[0][offset + i] = locations[0][offset + j];
                        i++;
                        j = j + steps;
                    }
                    step++;
                }
                offset = offset + totalNumberOfLocationsForThisLayer;
            }
        }
        return rearranged;
    }

    public float[][] convertLocationsToBoxes(float[][] locations, float[][] priors, float centerVariance, float sizeVariance){

        /** Convert regressional location results of
           SSD into boxes in the form of (center_x, center_y, h, w)
         */

        float[][] boxes = new float[locations.length][locations[0].length];
        for (int i = 0; i< locations.length; i++){
            for(int j = 0; j < 2 ; j++){
                boxes[i][j] = locations[i][j] * centerVariance * priors[i][j+2] + priors[i][j];
                boxes[i][j+2] = (float) (Math.exp(locations[i][j+2]* sizeVariance) * priors[i][j+2]);

            }

        }
        return boxes;
    }

    public float[][] centerFormToCornerForm(float[][] locations){

        /** Convert center from (center_x, center_y, h, w) to
         * corner form XMin, YMin, XMax, YMax
         */

        float[][] boxes = new float[locations.length][locations[0].length];
        for(int i = 0; i < locations.length; i++){
            for (int j = 0; j < 2; j++){
                boxes[i][j] = locations[i][j] - locations[i][j+2]/2;
                boxes[i][j+2] = locations[i][j] + locations[i][j+2]/2;
            }
        }
        return boxes;
    }

    public void print(float[][] arr){
        System.out.println(Arrays.deepToString(arr));
    }

    public static void printArrayList(ArrayList<float[]> alist)
    {
        System.out.println(Arrays.deepToString(alist.toArray()));
    }


    public static void print(float[] arr){
        System.out.println(Arrays.toString(arr));
    }

    public static float clamp(float val, float min, float max) {
        /** Clamp the value between min and max
         */

        return Math.max(min, Math.min(max, val));
    }



    public float[][] softmax2D(float[][] scores){

        /** compute softmax for each row
         * Will replace each row value with a value normalized by the sum of
         * all the values in the same row.
         */

        float[][] normalizedScores = new float[scores.length][scores[0].length];
        float rowSum;
        for(int i = 0; i < scores.length; i++){
            rowSum = 0.0f;
            for(int j = 0; j < scores[0].length; j++){
                rowSum = (float) (rowSum + Math.exp(scores[i][j]));
            }
            for(int j = 0; j < scores[0].length; j++){
                normalizedScores[i][j] = (float) (Math.exp(scores[i][j]) / rowSum);
            }
        }
        return normalizedScores;
    }
}