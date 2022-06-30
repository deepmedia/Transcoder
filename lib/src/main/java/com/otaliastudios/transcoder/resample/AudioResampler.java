package com.otaliastudios.transcoder.resample;

import androidx.annotation.NonNull;

import java.nio.Buffer;
import java.nio.ShortBuffer;

/**
 * Resamples audio data. See {@link UpsampleAudioResampler} or
 * {@link DownsampleAudioResampler} for concrete implementations.
 */
public interface AudioResampler {

    /**
     * Resamples input audio from input buffer into the output buffer.
     *
     * @param inputBuffer the input buffer
     * @param inputSampleRate the input sample rate
     * @param outputBuffer the output buffer
     * @param outputSampleRate the output sample rate
     * @param channels the number of channels
     */
    void resample(@NonNull final ShortBuffer inputBuffer, int inputSampleRate, @NonNull final ShortBuffer outputBuffer, int outputSampleRate, int channels);

    /**
     * createStream() and destroyStream() only to be implemented on Resamplers
     * following a stream approach for continuous input buffers. Not for static one shot methods
     * to resample.
     * @param inputSampleRate the input sample rate
     * @param outputSampleRate the output sample rate
     * @param numChannels the number of channels
     */
    void createStream(int inputSampleRate, int outputSampleRate, int numChannels);
    void destroyStream();

    AudioResampler DOWNSAMPLE = new DownsampleAudioResampler();

    AudioResampler UPSAMPLE = new UpsampleAudioResampler();

    AudioResampler PASSTHROUGH = new PassThroughAudioResampler();
}
