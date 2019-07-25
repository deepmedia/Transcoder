package com.otaliastudios.transcoder.stretch;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * A no-op {@link AudioStretcher} that copies input into output.
 */
public class PassThroughAudioStretcher implements AudioStretcher {

    @Override
    public void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels) {
        if (input.remaining() > output.remaining()) {
            throw new IllegalArgumentException("Illegal use of PassThroughAudioStretcher");
        }
        output.put(input);
    }
}
