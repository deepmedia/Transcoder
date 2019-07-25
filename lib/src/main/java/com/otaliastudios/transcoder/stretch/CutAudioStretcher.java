package com.otaliastudios.transcoder.stretch;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * A {@link AudioStretcher} meant to be used when output size is smaller than the input.
 * Cutting the latest samples is a way to go that does not modify the audio pitch.
 */
public class CutAudioStretcher implements AudioStretcher {

    @Override
    public void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels) {
        if (input.remaining() < output.remaining()) {
            throw new IllegalArgumentException("Illegal use of CutAudioStretcher");
        }
        int exceeding = input.remaining() - output.remaining();
        input.limit(input.limit() - exceeding); // Make remaining() the same for both
        output.put(input); // Safely bulk-put
        input.limit(input.limit() + exceeding); // Restore
        input.position(input.limit()); // Make as if we have read it all
    }
}
