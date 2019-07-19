package com.otaliastudios.transcoder.strategy.size;

import androidx.annotation.NonNull;

/**
 * A {@link Resizer} that returns the input size unchanged.
 */
@SuppressWarnings("unused")
public class PassThroughResizer implements Resizer {

    @SuppressWarnings("unused")
    public PassThroughResizer() { }

    @NonNull
    @Override
    public Size getOutputSize(@NonNull Size inputSize) {
        return inputSize;
    }
}
