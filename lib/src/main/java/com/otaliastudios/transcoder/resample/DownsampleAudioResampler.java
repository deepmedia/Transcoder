package com.otaliastudios.transcoder.resample;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * An {@link AudioResampler} that downsamples from a higher sample rate to a lower sample rate.
 */
public class DownsampleAudioResampler implements AudioResampler {

    private static float ratio(int remaining, int all) {
        return (float) remaining / all;
    }

    @Override
    public void resample(@NonNull ShortBuffer inputBuffer, int inputSampleRate, @NonNull ShortBuffer outputBuffer, int outputSampleRate, int channels) {
        if (inputSampleRate < outputSampleRate) {
            throw new IllegalArgumentException("Illegal use of DownsampleAudioResampler");
        }
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Illegal use of DownsampleAudioResampler. Channels:" + channels);
        }
        final int inputSamples = inputBuffer.remaining() / channels;
        final int dropSamples = (int) Math.floor((double) (outputBuffer.remaining() - inputBuffer.remaining()) / channels);
        int remainingInputSamples = inputSamples;
        int remainingDropSamples = dropSamples;
        float remainingInputSamplesRatio = ratio(remainingInputSamples, inputSamples);
        float remainingDropSamplesRatio = ratio(remainingDropSamples, dropSamples);
        while (remainingInputSamples > 0 && remainingDropSamples > 0) {
            // Will this be an input sample or a drop sample?
            // Choose the one with the bigger ratio.
            if (remainingInputSamplesRatio >= remainingDropSamplesRatio) {
                outputBuffer.put(inputBuffer.get());
                if (channels == 2) outputBuffer.put(inputBuffer.get());
                remainingInputSamples--;
                remainingInputSamplesRatio = ratio(remainingInputSamples, inputSamples);
            } else {
                // Drop this - read from input without writing.
                inputBuffer.position(inputBuffer.position() + channels);
                remainingDropSamples--;
                remainingDropSamplesRatio = ratio(remainingDropSamples, inputSamples);
            }
        }
    }
}
