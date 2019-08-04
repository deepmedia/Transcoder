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
     * The output buffer is guaranteed to have a {@link Buffer#remaining()} size that is
     * consistent with the sample rates difference. This means that the sample rate values
     * can be ignored and implementors can just think in terms of {@link Buffer#remaining()}
     * for both buffers.
     *
     * @param inputBuffer the input buffer
     * @param inputSampleRate the input sample rate
     * @param outputBuffer the output buffer
     * @param outputSampleRate the output sample rate
     * @param channels the number of channels
     */
    void resample(@NonNull final ShortBuffer inputBuffer, int inputSampleRate, @NonNull final ShortBuffer outputBuffer, int outputSampleRate, int channels);

    AudioResampler DOWNSAMPLE = new DownsampleAudioResampler();

    AudioResampler UPSAMPLE = new UpsampleAudioResampler();

    AudioResampler PASSTHROUGH = new PassThroughAudioResampler();
}
