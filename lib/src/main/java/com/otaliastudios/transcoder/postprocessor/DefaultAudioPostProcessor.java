package com.otaliastudios.transcoder.postprocessor;

import androidx.annotation.NonNull;
import java.nio.ShortBuffer;

public class DefaultAudioPostProcessor implements AudioPostProcessor {
    @Override
    public long calculateNewDurationUs(long durationUs) {
        return durationUs;
    }

    @Override
    public long postProcess(@NonNull ShortBuffer inputBuffer, @NonNull ShortBuffer outputBuffer, long bufferDurationUs) {
        outputBuffer.put(inputBuffer);
        return bufferDurationUs;
    }
}
