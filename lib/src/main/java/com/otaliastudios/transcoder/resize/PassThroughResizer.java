package com.otaliastudios.transcoder.resize;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.Size;
import com.otaliastudios.transcoder.resize.Resizer;

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
