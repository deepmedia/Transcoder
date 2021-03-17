package com.otaliastudios.transcoder.postprocessor;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

public class VolumeAudioPostProcessor implements AudioPostProcessor {
    private float mVolume;

    public VolumeAudioPostProcessor(float volume) {
        mVolume = volume;
    }

    @Override
    public long calculateNewDurationUs(long durationUs) {
        return durationUs;
    }

    private short applyVolume(short sample) {
        float sampleAtVolume = sample * mVolume;
        if (sampleAtVolume < Short.MIN_VALUE)
            sampleAtVolume = Short.MIN_VALUE;
        else if (sampleAtVolume > Short.MAX_VALUE)
            sampleAtVolume = Short.MAX_VALUE;
        return (short)sampleAtVolume;
    }

    @Override
    public long postProcess(@NonNull ShortBuffer inputBuffer, @NonNull ShortBuffer outputBuffer, long bufferDurationUs) {
        int inputRemaining = inputBuffer.remaining();
        for (int i=0; i<inputRemaining; i++) {
            outputBuffer.put(applyVolume(inputBuffer.get()));
        }
        return bufferDurationUs;
    }
}
