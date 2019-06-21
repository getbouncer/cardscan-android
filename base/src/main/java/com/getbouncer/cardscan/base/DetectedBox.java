package com.getbouncer.cardscan.base;

import android.support.annotation.NonNull;

public class DetectedBox implements Comparable {
    CGRect rect;
    int row;
    int col;
    private float confidence;
    private int numRows;
    private int numCols;
    private CGSize boxSize;
    private CGSize cardSize;
    private CGSize imageSize;

    DetectedBox(int row, int col, float confidence, int numRows, int numCols,
            CGSize boxSize, CGSize cardSize, CGSize imageSize) {

        // Resize the box to transform it from the model's coordinates into
        // the image's coordinates
        float w = boxSize.width * imageSize.width / cardSize.width;
        float h = boxSize.height * imageSize.height / cardSize.height;
        float x = (imageSize.width - w) / ((float) (numCols-1)) * ((float) col);
        float y = (imageSize.height - h) / ((float) (numRows-1)) * ((float) row);
        this.rect = new CGRect(x, y, w, h);
        this.row = row;
        this.col = col;
        this.confidence = confidence;
        this.numRows = numRows;
        this.numCols = numCols;
        this.boxSize = boxSize;
        this.cardSize = cardSize;
        this.imageSize = imageSize;
    }

    @Override
    public int compareTo(@NonNull Object o) {
        return Float.compare(this.confidence, ((DetectedBox) o).confidence);
    }
}
