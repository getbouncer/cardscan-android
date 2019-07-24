package com.getbouncer.cardscan.base.ssd;

public class PriorsGen{
    public static float[][] genPriors(int featureMapSize, int shrinkage, int boxSizeMin, int boxSizeMax, int aspecRatioOne, int aspectRatioTwo, int noOfPriors){
        float[][] boxes = new float[featureMapSize*featureMapSize*noOfPriors][4];
        float x_center, y_center;
        int image_size = 300;
        float size;
        float scale = image_size / shrinkage;
        float h, w;
        int priorIndex = 0;
        float ratioOne;
        float ratioTwo;

        for(int j = 0; j< featureMapSize; j++){
            for(int i = 0; i < featureMapSize; i++){
                x_center = (float) (i + 0.5) / scale;
                y_center = (float) (j + 0.5) / scale;

                size = boxSizeMin;
                h = w = (float) size / image_size;

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = h;
                boxes[priorIndex][3] = w;
                priorIndex++;

                size = (float) Math.sqrt(boxSizeMax * boxSizeMin);
                h = w = (float) size /image_size;

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = h;
                boxes[priorIndex][3] = w;
                priorIndex++;

                size = boxSizeMin;
                h = w = size / image_size;

                ratioOne =(float) Math.sqrt(aspecRatioOne);
                ratioTwo =(float) Math.sqrt(aspectRatioTwo);

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = h * ratioOne;
                boxes[priorIndex][3] = w / ratioOne;
                priorIndex++;

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = h / ratioOne;
                boxes[priorIndex][3] = w * ratioOne;
                priorIndex++;

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = h * ratioTwo;
                boxes[priorIndex][3] = w / ratioTwo;
                priorIndex++;

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = h / ratioTwo;
                boxes[priorIndex][3] = w * ratioTwo;
                priorIndex++;

            }
        }
        return boxes;
    }

    public static float[][] combinePriors(){

        float[][] priorsOne, priorsTwo, priorsCombined;

        priorsOne = PriorsGen.genPriors(19, 16, 60, 105, 2, 3, 6 );
        priorsTwo = PriorsGen.genPriors(10, 32, 105, 150, 2, 3, 6);

        priorsCombined = new float[priorsOne.length + priorsTwo.length][4];

        for(int i = 0; i < priorsOne.length; i++){
            for (int j = 0; j< priorsOne[0].length; j++){
                priorsCombined[i][j] = ArrUtils.clamp(priorsOne[i][j], 0.0f, 1.0f);
            }
        }


        for(int i = 0; i < priorsTwo.length; i++){
            for (int j = 0; j< priorsTwo[0].length; j++){
                priorsCombined[i+priorsOne.length][j] = ArrUtils.clamp(priorsTwo[i][j], 0.0f, 1.0f);
            }
        }

        return priorsCombined;
    }
}