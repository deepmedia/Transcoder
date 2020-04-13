package com.otaliastudios.transcoder.postprocessor;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

import static java.lang.Math.min;

public class MixerTargetAudioPostProcessor implements AudioPostProcessor {
    private MixerSourceAudioPostProcessor mSource;
    private float mSourceVolume;
    private float mTargetVolume;

    @Override
    public long calculateNewDurationUs(long durationUs) {
        return durationUs;
    }

    public MixerTargetAudioPostProcessor(MixerSourceAudioPostProcessor source, float sourceVolume, float targetVolume)
    {
        mSource = source;
        mSourceVolume = sourceVolume;
        mTargetVolume = targetVolume;
    }

    private short mixSamples(short sourceSample, short targetSample) {
        float mixedSample = (sourceSample * mSourceVolume)  + (targetSample * mTargetVolume);
        if (mixedSample < Short.MIN_VALUE)
            mixedSample = Short.MIN_VALUE;
        else if (mixedSample > Short.MAX_VALUE)
            mixedSample = Short.MAX_VALUE;
        return (short)mixedSample;
    }

    @Override
    public long postProcess(@NonNull ShortBuffer inputBuffer, @NonNull ShortBuffer outputBuffer, long bufferDurationUs) {
        ShortBuffer sourceBuffer = mSource.mBuffers.peek();
        int inputRemaining = inputBuffer.remaining();
        while (sourceBuffer != null && inputRemaining > 0) {
            int sourceRemaining = sourceBuffer.remaining();
            int remaining = min(inputRemaining, sourceRemaining);
            for (int i=0; i<remaining; i++) {
                outputBuffer.put(mixSamples(sourceBuffer.get(), inputBuffer.get()));
            }
            inputRemaining -= sourceRemaining;
            if (inputRemaining >= 0) {
                mSource.mBuffers.remove();
                sourceBuffer = mSource.mBuffers.peek();
            }
        }
        outputBuffer.put(inputBuffer);
        return bufferDurationUs;
    }
}
