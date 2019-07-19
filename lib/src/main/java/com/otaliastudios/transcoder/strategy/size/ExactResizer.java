package com.otaliastudios.transcoder.strategy.size;

import androidx.annotation.NonNull;

/**
 * A {@link Resizer} that returns the exact dimensions that were passed to the constructor.
 * Throws if the input size aspect ratio does not match.
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
        if (inputSize.getMinor() * output.getMajor() != inputSize.getMajor() * output.getMinor()) {
            throw new IllegalStateException("Input and output ratio do not match.");
        }
        return output;
    }
}
