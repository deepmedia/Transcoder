package com.otaliastudios.transcoder.postprocessor;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

public class VolumeAudioPostProcessor implements AudioPostProcessor {
    float mVolume;

    public VolumeAudioPostProcessor(float volume) {
        mVolume = volume;
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
    public void postProcess(@NonNull ShortBuffer inputBuffer, @NonNull ShortBuffer outputBuffer) {
        int inputRemaining = inputBuffer.remaining();
        for (int i=0; i<inputRemaining; i++) {
            outputBuffer.put(applyVolume(inputBuffer.get()));
        }
    }
}
