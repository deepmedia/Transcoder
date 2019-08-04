package com.otaliastudios.transcoder.resample;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * An {@link AudioResampler} that downsamples from a higher sample rate to a lower sample rate.
 */
public class DownsampleAudioResampler implements AudioResampler {

    @Override
    public void resample(@NonNull ShortBuffer inputBuffer, int inputSampleRate, @NonNull ShortBuffer outputBuffer, int outputSampleRate, int channels) {
        if (inputSampleRate < outputSampleRate) {
            throw new IllegalArgumentException("Illegal use of DownsampleAudioResampler");
        }
        // TODO
    }
}
