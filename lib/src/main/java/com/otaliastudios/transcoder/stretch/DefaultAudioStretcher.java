package com.otaliastudios.transcoder.stretch;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * An {@link AudioStretcher} that delegates to appropriate classes
 * based on input and output size.
 */
public class DefaultAudioStretcher implements AudioStretcher {

    @Override
    public void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels) {
        if (input.remaining() < output.remaining()) {
            INSERT.stretch(input, output, channels);
        } else if (input.remaining() > output.remaining()) {
            CUT.stretch(input, output, channels);
        } else {
            PASSTHROUGH.stretch(input, output, channels);
        }
    }
}
