package com.otaliastudios.transcoder.resize;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.ExactSize;
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
        float inputRatio = (float) inputSize.getFirst() / inputSize.getSecond();
        if(inputRatio > aspectRatio){
            int outputFirst = (int)(aspectRatio * ((float) inputSize.getSecond()));
            return new ExactSize(outputFirst, inputSize.getSecond());
        }else if(inputRatio < aspectRatio){
            int outputSecond = (int)(((float) inputSize.getFirst()) / aspectRatio);
            return new ExactSize(inputSize.getFirst(), outputSecond);
        }else{
            return new ExactSize(inputSize.getFirst(), inputSize.getSecond());
        }
    }
}
