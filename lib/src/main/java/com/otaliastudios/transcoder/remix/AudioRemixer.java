package com.otaliastudios.transcoder.remix;

import androidx.annotation.NonNull;

import java.nio.Buffer;
import java.nio.ShortBuffer;

/**
 * Remixes audio data. See {@link DownMixAudioRemixer},
 * {@link UpMixAudioRemixer} or {@link PassThroughAudioRemixer}
 * for concrete implementations.
 */
public interface AudioRemixer {

    /**
     * Remixes input audio from input buffer into the output buffer.
     * The output buffer is guaranteed to have a {@link Buffer#remaining()} size that is
     * consistent with {@link #getRemixedSize(int)}.
     *
     * @param inputBuffer the input buffer
     * @param outputBuffer the output buffer
     */
    void remix(@NonNull final ShortBuffer inputBuffer, @NonNull final ShortBuffer outputBuffer);

    /**
     * Returns the output size (in shorts) needed to process an input buffer of the
     * given size (in shorts).
     * @param inputSize input size in shorts
     * @return output size in shorts
     */
    int getRemixedSize(int inputSize);

    AudioRemixer DOWNMIX = new DownMixAudioRemixer();

    AudioRemixer UPMIX = new UpMixAudioRemixer();

    AudioRemixer PASSTHROUGH = new PassThroughAudioRemixer();
}
