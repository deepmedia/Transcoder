package com.otaliastudios.transcoder.postprocessor;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

public class MixerSourceAudioPostProcessor implements AudioPostProcessor {

    Queue<ShortBuffer> mBuffers = new ArrayDeque<>();

    @Override
    public long calculateNewDurationUs(long durationUs) {
        return 0;
    }

    @Override
    public long postProcess(@NonNull ShortBuffer inputBuffer, @NonNull ShortBuffer outputBuffer, long bufferDurationUs) {
        if (inputBuffer.remaining() > 0) {
            ShortBuffer sourceBuffer = ShortBuffer.allocate(inputBuffer.limit());
            sourceBuffer.put(inputBuffer);
            sourceBuffer.rewind();
            mBuffers.add(sourceBuffer);
        }
        return 0;
    }
}
