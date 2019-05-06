package com.getbouncer.cardscan;

import android.graphics.Bitmap;
import android.util.Log;

import java.util.ArrayList;

class RecognizeNumbers {
    private final int numRows;
    private final int numCols;
    private RecognizedDigits[][] recognizedDigits;
    private String number = null;
    private ArrayList<CGRect> numberBoxes;
    private final Bitmap image;

    RecognizeNumbers(Bitmap image, int numRows, int numCols) {
        this.image = image;
        this.numRows = numRows;
        this.numCols = numCols;

        this.recognizedDigits = new RecognizedDigits[numRows][numCols];
    }

    String number(RecognizedDigitsModel model, ArrayList<ArrayList<DetectedBox>> lines) {
        for (ArrayList<DetectedBox> line: lines) {
            String candidateNumber = "";

            for (DetectedBox word: line) {
                RecognizedDigits recognized = this.cachedDigits(model, word);
                if (recognized == null) {
                    return null;
                }

                candidateNumber += recognized.four();
            }

            if (candidateNumber.length() == 16  && CreditCardUtils.luhnCheck(candidateNumber)) {
                this.number = candidateNumber;
                this.numberBoxes = new ArrayList<CGRect>();
                for (DetectedBox box: line) {
                    this.numberBoxes.add(box.rect);
                }

                return candidateNumber;
            }
        }

        return null;
    }

    private String recognizeAmexDigits(RecognizedDigitsModel model, ArrayList<DetectedBox> line) {
        ArrayList<RecognizedDigits> recognizedDigits = new ArrayList<>();
        //Log.d("asdf", "staring amex");
        for (DetectedBox box: line) {
            recognizedDigits.add(cachedDigits(model, box));
            String debugString = recognizedDigits.get(recognizedDigits.size() - 1).debugString();
            //Log.d("asdf", debugString);
        }

        int startCol = line.get(0).col;
        int numCols = line.get(line.size() - 1).col + 8 - startCol;
        int positionsPerBox = 16;
        int numPositions = numCols * 2;
        ArrayList<Integer> digits = new ArrayList<>();
        for (int idx = 0; idx < numPositions; idx++) {
            digits.add(10);
        }

        for (int position = 0; position < numPositions; position++) {
            for (int idx = 0; idx < line.size(); idx++) {
                DetectedBox box = line.get(idx);
                RecognizedDigits recognized = recognizedDigits.get(idx);
                int boxPosition = (box.col  - startCol) * 2;
                if (position >= boxPosition && position < (boxPosition + positionsPerBox)) {
                    int digitIdx = position - boxPosition;
                    if (digits.get(position) == 10) {
                        digits.set(position, recognized.nonMaxSuppression().get(digitIdx));
                    }
                }
            }
        }

        for (int idx = 0; idx < (digits.size()-1); idx++) {
            if (digits.get(idx).equals(digits.get(idx+1))) {
                digits.set(idx, 10);
            }
        }

        String candidateNumber = "";
        for (Integer digit: digits) {
            if (digit != 10) {
                candidateNumber += digit;
            }
        }

        if (candidateNumber.length() == 15 && CreditCardUtils.luhnCheck(candidateNumber)) {
            return candidateNumber;
        }

        return null;
    }

    String amexNumber(RecognizedDigitsModel model, ArrayList<ArrayList<DetectedBox>> lines) {
        for (ArrayList<DetectedBox> line: lines) {
            String candidateNumber = recognizeAmexDigits(model, line);
            if (candidateNumber != null) {
                Log.d("asdf", candidateNumber);
                return candidateNumber;
            }
        }
        return null;
    }

    private RecognizedDigits cachedDigits(RecognizedDigitsModel model, DetectedBox box) {
        if (this.recognizedDigits[box.row][box.col] == null) {
            this.recognizedDigits[box.row][box.col] = RecognizedDigits.from(model, image, box.rect);
        }

        return this.recognizedDigits[box.row][box.col];
    }

}
