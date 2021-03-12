package com.otaliastudios.transcoder.internal.utils;

import android.media.MediaFormat;

/**
 * Utilities for bit rate estimation.
 */
public class BitRates {

    // For AVC this should be a reasonable default.
    // https://stackoverflow.com/a/5220554/4288782
    public static long estimateVideoBitRate(int width, int height, int frameRate) {
        return (long) (0.07F * 2 * width * height * frameRate);
    }

    // Wildly assuming a 0.75 compression rate for AAC.
    @SuppressWarnings("UnnecessaryLocalVariable")
    public static long estimateAudioBitRate(int channels, int sampleRate) {
        int bitsPerSample = 16;
        long samplesPerSecondPerChannel = (long) sampleRate;
        long bitsPerSecond = bitsPerSample * samplesPerSecondPerChannel * channels;
        double codecCompression = 0.75D; // Totally random.
        return (long) (bitsPerSecond * codecCompression);
    }
}
