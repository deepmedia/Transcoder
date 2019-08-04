package com.otaliastudios.transcoder.resample;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * An {@link AudioResampler} that upsamples from a lower sample rate to a higher sample rate.
 */
public class UpsampleAudioResampler implements AudioResampler {

    @Override
    public void resample(@NonNull ShortBuffer inputBuffer, int inputSampleRate, @NonNull ShortBuffer outputBuffer, int outputSampleRate, int channels) {
        if (inputSampleRate > outputSampleRate) {
            throw new IllegalArgumentException("Illegal use of UpsampleAudioResampler");
        }
        // TODO
    }
}
