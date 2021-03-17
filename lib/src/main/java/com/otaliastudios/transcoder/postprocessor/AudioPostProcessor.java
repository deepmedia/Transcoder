package com.otaliastudios.transcoder.postprocessor;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

public interface AudioPostProcessor extends PostProcessor {
    /**
     * Manipulates the raw audio data inside inputBuffer and put the result in outputBuffer
     * @param inputBuffer the input data (as raw audio data)
     * @param outputBuffer the data after the manipulation
     * @param bufferDurationUs the duration of the input data
     * @return the duration of the output data
     */
    long postProcess(@NonNull final ShortBuffer inputBuffer, @NonNull final ShortBuffer outputBuffer, long bufferDurationUs);
}
