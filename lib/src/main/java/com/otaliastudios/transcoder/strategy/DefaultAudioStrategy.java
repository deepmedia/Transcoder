package com.otaliastudios.transcoder.strategy;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.internal.MediaFormatConstants;

import androidx.annotation.NonNull;

import java.util.List;

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
    public TrackStatus createOutputFormat(@NonNull List<MediaFormat> inputFormats, @NonNull MediaFormat outputFormat) {

        int outputChannels = (channels == AUDIO_CHANNELS_AS_IS) ? getInputChannelCount(inputFormats) : channels;
        outputFormat.setString(MediaFormat.KEY_MIME, MediaFormatConstants.MIMETYPE_AUDIO_AAC);
        outputFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, getInputSampleRate(inputFormats));
        outputFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, outputChannels);
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, getInputBitRate(inputFormats));
        return TrackStatus.COMPRESSING;
    }

    private int getInputChannelCount(@NonNull List<MediaFormat> formats) {
        int count = 0;
        for (MediaFormat format : formats) {
            count = Math.max(count, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        }
        return count;
    }

    private int getInputSampleRate(@NonNull List<MediaFormat> formats) {
        int rate = formats.get(0).getInteger(MediaFormat.KEY_SAMPLE_RATE);
        for (MediaFormat format : formats) {
            if (rate != format.getInteger(MediaFormat.KEY_SAMPLE_RATE)) {
                throw new IllegalArgumentException("All input formats should have the same sample rate.");
            }
        }
        return rate;
    }

    private int getInputBitRate(@NonNull List<MediaFormat> formats) {
        int rate = formats.get(0).getInteger(MediaFormat.KEY_BIT_RATE);
        for (MediaFormat format : formats) {
            if (rate != format.getInteger(MediaFormat.KEY_BIT_RATE)) {
                throw new IllegalArgumentException("All input formats should have the same bit rate.");
            }
        }
        return rate;
    }
}
