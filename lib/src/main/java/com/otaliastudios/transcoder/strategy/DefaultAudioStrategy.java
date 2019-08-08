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

    public final static int CHANNELS_AS_INPUT = -1;
    public final static int SAMPLE_RATE_AS_INPUT = -1;

    /**
     * Holds configuration values.
     */
    @SuppressWarnings("WeakerAccess")
    public static class Options {
        private Options() {}
        private int channels;
        private int sampleRate;
    }

    /**
     * Creates a new {@link DefaultAudioStrategy.Builder}.
     *
     * @return a strategy builder
     */
    @NonNull
    @SuppressWarnings("unused")
    public static DefaultAudioStrategy.Builder builder() {
        return new DefaultAudioStrategy.Builder();
    }

    public static class Builder {
        private int channels = CHANNELS_AS_INPUT;
        private int sampleRate = SAMPLE_RATE_AS_INPUT;

        @SuppressWarnings({"unused", "WeakerAccess"})
        public Builder() { }

        @NonNull
        public Builder channels(int channels) {
            this.channels = channels;
            return this;
        }

        @NonNull
        public Builder sampleRate(int sampleRate) {
            this.sampleRate = sampleRate;
            return this;
        }

        @NonNull
        @SuppressWarnings("WeakerAccess")
        public DefaultAudioStrategy.Options options() {
            DefaultAudioStrategy.Options options = new DefaultAudioStrategy.Options();
            options.channels = channels;
            options.sampleRate = sampleRate;
            return options;
        }

        @NonNull
        public DefaultAudioStrategy build() {
            return new DefaultAudioStrategy(options());
        }
    }

    private Options options;

    @SuppressWarnings("WeakerAccess")
    public DefaultAudioStrategy(@NonNull Options options) {
        this.options = options;
    }

    @NonNull
    @Override
    public TrackStatus createOutputFormat(@NonNull List<MediaFormat> inputFormats, @NonNull MediaFormat outputFormat) {
        int outputChannels = (options.channels == CHANNELS_AS_INPUT) ? getInputChannelCount(inputFormats) : options.channels;
        int outputSampleRate = (options.sampleRate == SAMPLE_RATE_AS_INPUT) ? getInputSampleRate(inputFormats) : options.sampleRate;
        outputFormat.setString(MediaFormat.KEY_MIME, MediaFormatConstants.MIMETYPE_AUDIO_AAC);
        outputFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, outputSampleRate);
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

    private int getInputSampleRate(@NonNull List<MediaFormat> formats) {
        int minRate = Integer.MAX_VALUE;
        for (MediaFormat format : formats) {
            minRate = Math.min(minRate, format.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        }
        // Since this is a quality parameter, it makes sense to take the lowest.
        // This is very important to avoid useless upsampling in concatenated videos,
        // also because our upsample algorithm is not that good.
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
