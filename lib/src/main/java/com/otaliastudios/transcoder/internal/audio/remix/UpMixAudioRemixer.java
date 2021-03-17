package com.otaliastudios.transcoder.internal.audio.remix;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.internal.audio.remix.AudioRemixer;

import java.nio.ShortBuffer;

/**
 * A {@link AudioRemixer} that upmixes mono audio to stereo.
 */
public class UpMixAudioRemixer implements AudioRemixer {

    @Override
    public void remix(@NonNull final ShortBuffer inputBuffer, @NonNull final ShortBuffer outputBuffer) {
        // Up-mix mono to stereo
        final int inRemaining = inputBuffer.remaining();
        final int outSpace = outputBuffer.remaining() / 2;

        final int samplesToBeProcessed = Math.min(inRemaining, outSpace);
        for (int i = 0; i < samplesToBeProcessed; ++i) {
            final short inSample = inputBuffer.get();
            outputBuffer.put(inSample);
            outputBuffer.put(inSample);
        }
    }

    @Override
    public int getRemixedSize(int inputSize) {
        return inputSize * 2;
    }
}
