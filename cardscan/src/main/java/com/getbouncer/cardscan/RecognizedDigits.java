package com.getbouncer.cardscan;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.Collections;

class RecognizedDigits {
    private static final int kNumPredictions = RecognizedDigitsModel.kNumPredictions;
    private ArrayList<Integer> digits;
    private ArrayList<Float> confidence;

    private static final int kBackgroundClass = 10;
    private static final float kDigitMinConfidence = (float) 0.15;

    private RecognizedDigits(ArrayList<Integer> digits, ArrayList<Float> confidence) {
        this.digits = digits;
        this.confidence = confidence;
    }

    static RecognizedDigits from(RecognizedDigitsModel model, Bitmap image, CGRect box) {
        final Bitmap frame = Bitmap.createBitmap(image, Math.round(box.x), Math.round(box.y),
                (int) box.width, (int) box.height);
        model.classifyFrame(frame);

        ArrayList<Integer> digits = new ArrayList<>();
        ArrayList<Float> confidence = new ArrayList<>();

        for (int col = 0; col < kNumPredictions; col++) {
            RecognizedDigitsModel.ArgMaxAndConfidence argAndConf = model.argAndValueMax(col);

            if (argAndConf.confidence < kDigitMinConfidence) {
                digits.add(kBackgroundClass);
            } else {
                digits.add(argAndConf.argMax);
            }
            confidence.add(argAndConf.confidence);
        }

        return new RecognizedDigits(digits, confidence);
    }

    ArrayList<Integer> nonMaxSuppression() {
        ArrayList<Integer> digits = new ArrayList<>();
        ArrayList<Float> confidence = new ArrayList<>();
        digits.addAll(this.digits);
        confidence.addAll(this.confidence);

        // greedy non max suppression
        for (int idx = 0; idx < (kNumPredictions-1); idx++) {
            if (digits.get(idx) != kBackgroundClass && digits.get(idx+1) != kBackgroundClass) {
                if (confidence.get(idx) < confidence.get(idx+1)) {
                    digits.set(idx, kBackgroundClass);
                    confidence.set(idx, (float) 1.0);
                } else {
                    digits.set(idx+1, kBackgroundClass);
                    confidence.set(idx+1, (float) 1.0);
                }
            }
        }

        return digits;
    }

    String debugString() {
        ArrayList<Integer> digits = nonMaxSuppression();
        String result = "";
        for (Integer digit:digits) {
            if (digit != kBackgroundClass) {
                result += digit;
            } else {
                result += "-";
            }
        }
        return result;
    }

    String stringResult() {
        ArrayList<Integer> digits = nonMaxSuppression();
        String result = "";
        for (Integer digit:digits) {
            if (digit != kBackgroundClass) {
                result += digit;
            }
        }
        return result;
    }

    String four() {
        ArrayList<Integer> digits = nonMaxSuppression();
        String result = stringResult();

        if (result.length() < 4) {
            return "";
        }

        // since we know that we have too many digits, trim from the outer most digits. Since we
        // designed our detection model to center digits, this should work
        boolean fromLeft = true;
        int leftIdx = 0;
        int rightIdx = digits.size() - 1;
        while (result.length() > 4) {
            if (fromLeft) {
                if (digits.get(leftIdx) != kBackgroundClass) {
                    result = result.substring(1);
                    digits.set(leftIdx, kBackgroundClass);
                }
                fromLeft = false;
                leftIdx += 1;
            } else {
                if (digits.get(rightIdx) != kBackgroundClass) {
                    result = result.substring(0, result.length() - 1);
                    digits.set(rightIdx, kBackgroundClass);
                }
                fromLeft = true;
                rightIdx -= 1;
            }
        }

        // as a last error check make sure that all of the digits are equally
        // spaced and reject the whole lot if they aren't. This can fix errors
        // on cards with hard to read digits and small fonts where it can sometimes
        // pick up edge digits from another group.
        ArrayList<Integer> positions = new ArrayList<>();
        for (int idx = 0; idx < digits.size(); idx++) {
            if (digits.get(idx) != kBackgroundClass) {
                positions.add(idx);
            }
        }
        ArrayList<Integer> deltas = new ArrayList<>();
        // start from one to compare neighbor values
        for (int idx = 1; idx < positions.size(); idx++) {
            deltas.add(positions.get(idx) - positions.get(idx-1));
        }

        Collections.sort(deltas);
        int maxDelta = deltas.get(deltas.size() - 1);
        int minDelta = deltas.get(0);

        if (maxDelta > (minDelta+1)) {
            return "";
        }

        return result;
    }
}
