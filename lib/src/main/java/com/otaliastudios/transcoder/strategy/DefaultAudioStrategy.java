package com.otaliastudios.transcoder.strategy;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.internal.MediaFormatConstants;

import androidx.annotation.NonNull;

/**
 * An {@link TrackStrategy} for audio that converts it to AAC with the given number
 * of channels.
 */
public class DefaultAudioStrategy implements TrackStrategy {

    public static final int AUDIO_CHANNELS_AS_IS = -1;

    private int channels;

    public DefaultAudioStrategy(int channels) {
        this.channels = channels;
    }

    @NonNull
    @Override
    public TrackStatus createOutputFormat(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat) {
        int inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int outputChannels = (channels == AUDIO_CHANNELS_AS_IS) ? inputChannels : channels;
        outputFormat.setString(MediaFormat.KEY_MIME, MediaFormatConstants.MIMETYPE_AUDIO_AAC);
        outputFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        outputFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, outputChannels);
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, inputFormat.getInteger(MediaFormat.KEY_BIT_RATE));
        return TrackStatus.COMPRESSING;
    }

}
