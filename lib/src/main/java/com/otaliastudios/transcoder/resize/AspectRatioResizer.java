package com.otaliastudios.transcoder.resize;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.Size;
import com.otaliastudios.transcoder.resize.Resizer;

/**
 * A {@link Resizer} that crops the input size to match the given
 * aspect ratio, respecting the source portrait or landscape-ness.
 */
public class AspectRatioResizer implements Resizer {

    private final float aspectRatio;

    /**
     * Creates a new resizer.
     * @param aspectRatio the desired aspect ratio
     */
    public AspectRatioResizer(float aspectRatio) {
        this.aspectRatio = aspectRatio;
    }

    @NonNull
    @Override
    public Size getOutputSize(@NonNull Size inputSize) {
        float inputRatio = (float) inputSize.getMajor() / inputSize.getMinor();
        float outputRatio = aspectRatio > 1 ? aspectRatio : 1F / aspectRatio;
        // now both are greater than 1 (major / minor).
        if (inputRatio > outputRatio) {
            // input is "wider". We must reduce the input major dimension.
            return new Size(inputSize.getMinor(), (int) (outputRatio * inputSize.getMinor()));
        } else if (inputRatio < outputRatio) {
            // input is more square. We must reduce the input minor dimension.
            return new Size(inputSize.getMajor(), (int) (inputSize.getMajor() / outputRatio));
        } else {
            return inputSize;
        }
    }
}
