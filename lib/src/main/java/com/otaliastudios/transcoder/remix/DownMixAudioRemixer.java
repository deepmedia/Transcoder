package com.otaliastudios.transcoder.remix;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * A {@link AudioRemixer} that downmixes stereo audio to mono.
 */
public class DownMixAudioRemixer implements AudioRemixer {

    private static final int SIGNED_SHORT_LIMIT = 32768;
    private static final int UNSIGNED_SHORT_MAX = 65535;

    @Override
    public void remix(@NonNull final ShortBuffer inputBuffer, @NonNull final ShortBuffer outputBuffer) {
        // Down-mix stereo to mono
        // Viktor Toth's algorithm -
        // See: http://www.vttoth.com/CMS/index.php/technical-notes/68
        //      http://stackoverflow.com/a/25102339
        final int inRemaining = inputBuffer.remaining() / 2;
        final int outSpace = outputBuffer.remaining();

        final int samplesToBeProcessed = Math.min(inRemaining, outSpace);
        for (int i = 0; i < samplesToBeProcessed; ++i) {
            // Convert to unsigned
            final int a = inputBuffer.get() + SIGNED_SHORT_LIMIT;
            final int b = inputBuffer.get() + SIGNED_SHORT_LIMIT;
            int m;
            // Pick the equation
            if ((a < SIGNED_SHORT_LIMIT) || (b < SIGNED_SHORT_LIMIT)) {
                // Viktor's first equation when both sources are "quiet"
                // (i.e. less than middle of the dynamic range)
                m = a * b / SIGNED_SHORT_LIMIT;
            } else {
                // Viktor's second equation when one or both sources are loud
                m = 2 * (a + b) - (a * b) / SIGNED_SHORT_LIMIT - UNSIGNED_SHORT_MAX;
            }
            // Convert output back to signed short
            if (m == UNSIGNED_SHORT_MAX + 1) m = UNSIGNED_SHORT_MAX;
            outputBuffer.put((short) (m - SIGNED_SHORT_LIMIT));
        }
    }

    @Override
    public int getRemixedSize(int inputSize) {
        return inputSize / 2;
    }
}
