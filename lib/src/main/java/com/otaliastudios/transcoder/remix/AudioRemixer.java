package com.otaliastudios.transcoder.remix;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * Remixes audio data. See {@link DownMixAudioRemixer},
 * {@link UpMixAudioRemixer} or {@link PassThroughAudioRemixer}
 * for concrete implementations.
 */
public interface AudioRemixer {

    void remix(@NonNull final ShortBuffer inputBuffer, @NonNull final ShortBuffer outputBuffer);

    int getRemixedSize(int inputSize);

    AudioRemixer DOWNMIX = new DownMixAudioRemixer();

    AudioRemixer UPMIX = new UpMixAudioRemixer();

    AudioRemixer PASSTHROUGH = new PassThroughAudioRemixer();
}
