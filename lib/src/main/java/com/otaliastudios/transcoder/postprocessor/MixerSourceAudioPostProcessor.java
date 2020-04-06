package com.otaliastudios.transcoder.postprocessor;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

public class MixerSourceAudioPostProcessor implements AudioPostProcessor {

    Queue<ShortBuffer> mBuffers = new ArrayDeque<>();

    @Override
    public void postProcess(@NonNull ShortBuffer inputBuffer, @NonNull ShortBuffer outputBuffer) {
        if (inputBuffer.remaining() > 0) {
            ShortBuffer sourceBuffer = ShortBuffer.allocate(inputBuffer.capacity());
            sourceBuffer.put(inputBuffer);
            sourceBuffer.rewind();
            mBuffers.add(sourceBuffer);
        }
    }
}
