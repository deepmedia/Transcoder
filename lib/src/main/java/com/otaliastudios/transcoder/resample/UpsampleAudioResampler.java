package com.otaliastudios.transcoder.resample;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * An {@link AudioResampler} that upsamples from a lower sample rate to a higher sample rate.
 */
public class UpsampleAudioResampler implements AudioResampler {

    private static float ratio(int remaining, int all) {
        return (float) remaining / all;
    }

    @Override
    public void resample(@NonNull ShortBuffer inputBuffer, int inputSampleRate, @NonNull ShortBuffer outputBuffer, int outputSampleRate, int channels) {
        if (inputSampleRate > outputSampleRate) {
            throw new IllegalArgumentException("Illegal use of UpsampleAudioResampler");
        }
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("Illegal use of UpsampleAudioResampler. Channels:" + channels);
        }
        final int inputSamples = inputBuffer.remaining() / channels;
        final int fakeSamples = (int) Math.floor((double) (outputBuffer.remaining() - inputBuffer.remaining()) / channels);
        int remainingInputSamples = inputSamples;
        int remainingFakeSamples = fakeSamples;
        float remainingInputSamplesRatio = ratio(remainingInputSamples, inputSamples);
        float remainingFakeSamplesRatio = ratio(remainingFakeSamples, fakeSamples);
        while (remainingInputSamples > 0 && remainingFakeSamples > 0) {
            // Will this be an input sample or a fake sample?
            // Choose the one with the bigger ratio.
            if (remainingInputSamplesRatio >= remainingFakeSamplesRatio) {
                outputBuffer.put(inputBuffer.get());
                if (channels == 2) outputBuffer.put(inputBuffer.get());
                remainingInputSamples--;
                remainingInputSamplesRatio = ratio(remainingInputSamples, inputSamples);
            } else {
                outputBuffer.put(outputBuffer.get(outputBuffer.position() - channels));
                if (channels == 2) outputBuffer.put(outputBuffer.get(outputBuffer.position() - channels));
                remainingFakeSamples--;
                remainingFakeSamplesRatio = ratio(remainingFakeSamples, inputSamples);
            }
        }
    }
}
