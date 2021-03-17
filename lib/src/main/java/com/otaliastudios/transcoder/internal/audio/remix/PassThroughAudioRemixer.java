package com.otaliastudios.transcoder.internal.audio.remix;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.internal.audio.remix.AudioRemixer;

import java.nio.ShortBuffer;

/**
 * The simplest {@link AudioRemixer} that does nothing.
 */
public class PassThroughAudioRemixer implements AudioRemixer {

    @Override
    public void remix(@NonNull final ShortBuffer inputBuffer, @NonNull final ShortBuffer outputBuffer) {
        outputBuffer.put(inputBuffer);
    }

    @Override
    public int getRemixedSize(int inputSize) {
        return inputSize;
    }
}
