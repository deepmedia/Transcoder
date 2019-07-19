package com.otaliastudios.transcoder.strategy.size;

import androidx.annotation.NonNull;

/**
 * A {@link Resizer} that returns the exact dimensions that were passed to the constructor.
 */
public class ExactResizer implements Resizer {

    private final Size output;

    public ExactResizer(int first, int second) {
        output = new Size(first, second);
    }

    @SuppressWarnings("unused")
    public ExactResizer(@NonNull Size size) {
        output = size;
    }

    @NonNull
    @Override
    public Size getOutputSize(@NonNull Size inputSize) {
        // We now support different aspect ratios, but could make this check below configurable.
        /* if (inputSize.getMinor() * output.getMajor() != inputSize.getMajor() * output.getMinor()) {
            throw new IllegalStateException("Input and output ratio do not match.");
        } */
        return output;
    }
}
