package com.otaliastudios.transcoder.strategy;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.otaliastudios.transcoder.internal.MediaFormatConstants;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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

    @Nullable
    @Override
    public MediaFormat createOutputFormat(@NonNull MediaFormat inputFormat) throws TrackStrategyException {
        int inputChannels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        int outputChannels = (channels == AUDIO_CHANNELS_AS_IS) ? inputChannels : channels;
        final MediaFormat format = MediaFormat.createAudioFormat(MediaFormatConstants.MIMETYPE_AUDIO_AAC,
                inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE), outputChannels);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, inputFormat.getInteger(MediaFormat.KEY_BIT_RATE));
        return format;
    }
}
