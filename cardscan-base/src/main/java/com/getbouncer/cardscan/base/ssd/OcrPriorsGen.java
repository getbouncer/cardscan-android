
package com.getbouncer.cardscan.base.ssd;

public class OcrPriorsGen{

    public static float[][] genPriors(int featureMapSize_height, int featureMapSize_width, int shrinkage_height,
                                      int shrinkage_width, int boxSizeMin, int boxSizeMax, int aspecRatioOne,
                                      int noOfPriors){
        float[][] boxes = new float[featureMapSize_height*featureMapSize_width*noOfPriors][4];
        float x_center, y_center;
        int image_height = 375;
        int image_width = 600;
        float size;
        float scale_height = image_height / shrinkage_height;
        float scale_width = image_width / shrinkage_width;
        float h, w;
        int priorIndex = 0;
        float ratioOne;

        for(int j = 0; j< featureMapSize_height; j++){
            for(int i = 0; i < featureMapSize_width; i++){
                x_center = (float) (i + 0.5) / scale_width;
                y_center = (float) (j + 0.5) / scale_height;

                size = boxSizeMin;

                h = (float) size / image_height;
                w = (float) size / image_width;

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = w;
                boxes[priorIndex][3] = h;
                priorIndex++;

                size = (float) Math.sqrt(boxSizeMax * boxSizeMin);
                h = (float) size / image_height;
                w = (float) size / image_width;


                ratioOne =(float) Math.sqrt(aspecRatioOne);

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = w;
                boxes[priorIndex][3] = h * ratioOne;
                priorIndex++;

                size = boxSizeMin;
                h = (float) size / image_height;
                w = (float) size / image_width;
                ratioOne =(float) Math.sqrt(aspecRatioOne);

                boxes[priorIndex][0] = x_center;
                boxes[priorIndex][1] = y_center;
                boxes[priorIndex][2] = w;
                boxes[priorIndex][3] = h * ratioOne;
                priorIndex++;

            }
        }
        return boxes;
    }

    public static float[][] combinePriors(){

        float[][] priorsOne, priorsTwo, priorsCombined;

        priorsOne = OcrPriorsGen.genPriors(24, 38, 16, 16, 14, 30, 3, 3);
        priorsTwo = OcrPriorsGen.genPriors(12, 19, 31, 31, 30, 45, 3, 3);

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
