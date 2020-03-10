package com.getbouncer.cardscan.base;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class UXModelResult {

    public final float noCardScore;
    public final float panSideScore;
    public final float noPanSideScore;
    private float[] modelOutput;
    private float maxScore;
    @Nullable private UXModelEnum uxModelEnum;

    public UXModelResult(@NonNull float[] modelOutput) {
        noCardScore = modelOutput[0];
        panSideScore = modelOutput[1];
        noPanSideScore = modelOutput[2];
        this.modelOutput = modelOutput;

        this.calculateResult();
    }

    public enum UXModelEnum {
        NO_PAN_SIDE,
        NO_CARD,
        PAN_SIDE
    }

    @Nullable
    public UXModelEnum getResult() {
        return this.uxModelEnum;
    }

    public float getMaxScore() {
        return this.maxScore;
    }

    private void calculateResult() {
        int maxIndex = -1;
        float maxValue = -1;
        for (int i = 0; i < modelOutput.length; i++) {
            if (modelOutput[i] > maxValue) {
                maxValue = modelOutput[i];
                maxIndex = i;
            }
        }
        maxScore = maxValue;
        if (maxIndex == 0) {
            uxModelEnum = UXModelEnum.NO_PAN_SIDE;
        } else if (maxIndex == 1) {
            uxModelEnum = UXModelEnum.NO_CARD;
        } else if (maxIndex == 2) {
            //uxModelEnum = UXModelEnum.NO_PAN_SIDE;
            uxModelEnum = UXModelEnum.PAN_SIDE;
        } else {
            throw new EnumConstantNotPresentException(UXModelEnum.class, "Unexpected enum value " + maxIndex);
        }
    }
}
