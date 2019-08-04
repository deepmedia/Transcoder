package com.otaliastudios.transcoder.strategy;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.MediaFormatConstants;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * An {@link TrackStrategy} for audio that converts it to AAC with the given number
 * of channels.
 */
public class DefaultAudioStrategy implements TrackStrategy {

    private final static String TAG = DefaultAudioStrategy.class.getSimpleName();
    private final static Logger LOG = new Logger(TAG);

    public final static int AUDIO_CHANNELS_AS_IS = -1;

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
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, getAverageInputBitRate(inputFormats));
        return TrackStatus.COMPRESSING;
    }

    private int getInputChannelCount(@NonNull List<MediaFormat> formats) {
        int count = 0;
        for (MediaFormat format : formats) {
            count = Math.max(count, format.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        }
        return count;
    }

    // Since this is a quality parameter, it makes sense to take the lowest.
    private int getInputSampleRate(@NonNull List<MediaFormat> formats) {
        int minRate = Integer.MAX_VALUE;
        for (MediaFormat format : formats) {
            minRate = Math.min(minRate, format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        }
        return minRate;
    }

    private int getAverageInputBitRate(@NonNull List<MediaFormat> formats) {
        int count = formats.size();
        double bitRate = 0;
        for (MediaFormat format : formats) {
            bitRate += format.getInteger(MediaFormat.KEY_BIT_RATE);
        }
        return (int) (bitRate / count);
    }
}
