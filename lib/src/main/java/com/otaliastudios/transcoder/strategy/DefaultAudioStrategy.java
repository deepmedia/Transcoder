package com.otaliastudios.transcoder.strategy;

import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.internal.BitRates;
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

    @SuppressWarnings("WeakerAccess")
    public final static long BITRATE_UNKNOWN = Long.MIN_VALUE;

    /**
     * Holds configuration values.
     */
    @SuppressWarnings("WeakerAccess")
    public static class Options {
        private Options() {}
        private int targetChannels;
        private int targetSampleRate;
        private long targetBitRate;
        private String targetMimeType;
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
        private int targetChannels = CHANNELS_AS_INPUT;
        private int targetSampleRate = SAMPLE_RATE_AS_INPUT;
        private long targetBitRate = BITRATE_UNKNOWN;
        private String targetMimeType = MediaFormatConstants.MIMETYPE_AUDIO_AAC;

        @SuppressWarnings({"unused", "WeakerAccess"})
        public Builder() { }

        @NonNull
        public Builder channels(int channels) {
            targetChannels = channels;
            return this;
        }

        @NonNull
        public Builder sampleRate(int sampleRate) {
            targetSampleRate = sampleRate;
            return this;
        }

        /**
         * The desired bit rate. Can optionally be {@link #BITRATE_UNKNOWN},
         * in which case the strategy will try to estimate the bitrate.
         * @param bitRate desired bit rate (bits per second)
         * @return this for chaining
         */
        @NonNull
        public Builder bitRate(long bitRate) {
            targetBitRate = bitRate;
            return this;
        }

        @NonNull
        public Builder mimeType(@NonNull String mimeType) {
            targetMimeType = mimeType;
            return this;
        }

        @NonNull
        @SuppressWarnings("WeakerAccess")
        public DefaultAudioStrategy.Options options() {
            DefaultAudioStrategy.Options options = new DefaultAudioStrategy.Options();
            options.targetChannels = targetChannels;
            options.targetSampleRate = targetSampleRate;
            options.targetMimeType = targetMimeType;
            options.targetBitRate = targetBitRate;
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
    public TrackStatus createOutputFormat(@NonNull List<MediaFormat> inputFormats,
                                          @NonNull MediaFormat outputFormat) {
        int outputChannels = (options.targetChannels == CHANNELS_AS_INPUT)
                ? getInputChannelCount(inputFormats)
                : options.targetChannels;
        int outputSampleRate = (options.targetSampleRate == SAMPLE_RATE_AS_INPUT)
                ? getInputSampleRate(inputFormats)
                : options.targetSampleRate;
        long outputBitRate;
        if (inputFormats.size() == 1
                && options.targetChannels == CHANNELS_AS_INPUT
                && options.targetSampleRate == SAMPLE_RATE_AS_INPUT
                && options.targetBitRate == BITRATE_UNKNOWN
                && inputFormats.get(0).containsKey(MediaFormat.KEY_BIT_RATE)) {
            // Special case: if we have only a single input format, and channels and sample rate
            // were unchanged, and bit rate is available, use that instead of estimating.
            outputBitRate = inputFormats.get(0).getInteger(MediaFormat.KEY_BIT_RATE);
        } else {
            // Normal case: just use the user provided bit rate or try to estimate it with our
            // new channels and sample rate values.
            outputBitRate = (options.targetBitRate == BITRATE_UNKNOWN)
                    ? BitRates.estimateAudioBitRate(outputChannels, outputSampleRate)
                    : options.targetBitRate;
        }
        outputFormat.setString(MediaFormat.KEY_MIME, options.targetMimeType);
        outputFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, outputSampleRate);
        outputFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, outputChannels);
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, (int) outputBitRate);
        if (MediaFormatConstants.MIMETYPE_AUDIO_AAC.equalsIgnoreCase(options.targetMimeType)) {
            outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        }
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
}
