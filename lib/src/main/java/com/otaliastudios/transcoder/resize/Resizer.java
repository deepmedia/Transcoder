package com.otaliastudios.transcoder.resize;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.ExactSize;
import com.otaliastudios.transcoder.common.Size;

/**
 * A general purpose interface that can be used (accepted as a parameter)
 * by video strategies such as {@link com.otaliastudios.transcoder.strategy.DefaultVideoStrategy}
 * to compute the output size.
 *
 * Note that a {@link Size} itself has no notion of which dimension is width and which is height.
 * The video strategy that consumes this resizer will check the input orientation (portrait / landscape)
 * so that they match.
 *
 * To avoid this behavior and set exact width and height, instances can return an {@link ExactSize}.
 * In this case, width and height will be used as defined without checking for portrait / landscapeness
 * of input.
 *
 * However, the final displayed video might be rotated because it might have a non-zero rotation tag
 * in metadata (this is frequently the case).
 */
public interface Resizer {

    /**
     * Parses the input size and returns the output.
     * This method should throw an exception if the input size is not processable.
     * @param inputSize the input video size
     * @return the output video size
     * @throws Exception if something is wrong with input size
     */
    @NonNull
    Size getOutputSize(@NonNull Size inputSize) throws Exception;
}
