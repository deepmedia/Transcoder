package com.otaliastudios.transcoder.resize;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.Size;
import com.otaliastudios.transcoder.resize.Resizer;

/**
 * A {@link Resizer} that scales down the input size so that its dimension
 * is smaller or equal to a certain value.
 */
public class AtMostResizer implements Resizer {

    private final int atMostMinor;
    private final int atMostMajor;

    /**
     * Checks just the minor dimension.
     * @param atMost the dimension constraint
     */
    public AtMostResizer(int atMost) {
        atMostMinor = atMost;
        atMostMajor = Integer.MAX_VALUE;
    }

    /**
     * Checks both dimensions.
     * @param atMostMinor the minor dimension constraint
     * @param atMostMajor the major dimension constraint
     */
    public AtMostResizer(int atMostMinor, int atMostMajor) {
        this.atMostMinor = atMostMinor;
        this.atMostMajor = atMostMajor;
    }

    @NonNull
    @Override
    public Size getOutputSize(@NonNull Size inputSize) {
        if (inputSize.getMinor() <= atMostMinor && inputSize.getMajor() <= atMostMajor) {
            // No compression needed here.
            return inputSize;
        }
        int outMinor, outMajor;
        float minorScale = (float) inputSize.getMinor() / atMostMinor; // approx. 0 if not needed
        float maiorScale = (float) inputSize.getMajor() / atMostMajor; // > 1 if needed.
        float inputRatio = (float) inputSize.getMinor() / inputSize.getMajor();
        if (maiorScale >= minorScale) {
            outMajor = atMostMajor;
            outMinor = (int) ((float) outMajor * inputRatio);
        } else {
            outMinor = atMostMinor;
            outMajor = (int) ((float) outMinor / inputRatio);
        }
        if (outMinor % 2 != 0) outMinor--;
        if (outMajor % 2 != 0) outMajor--;
        return new Size(outMinor, outMajor);
    }
}
