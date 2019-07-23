package com.otaliastudios.transcoder.transcode;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.engine.TranscoderMuxer;
import com.otaliastudios.transcoder.internal.MediaCodecBuffers;

import java.io.IOException;

/**
 * A base implementation of {@link TrackTranscoder} that reads
 * from {@link MediaExtractor} and does feeding and draining job.
 */
public abstract class BaseTrackTranscoder implements TrackTranscoder {

    private static final int DRAIN_STATE_NONE = 0;
    private static final int DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY = 1;
    private static final int DRAIN_STATE_CONSUMED = 2;

    private final MediaExtractor mExtractor;
    private final int mTrackIndex;
    private final TranscoderMuxer mMuxer;
    private final TrackType mTrackType;

    private long mWrittenPresentationTimeUs;

    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private MediaCodec mDecoder;
    private MediaCodec mEncoder;
    private MediaCodecBuffers mDecoderBuffers;
    private MediaCodecBuffers mEncoderBuffers;
    private boolean mDecoderStarted;
    private boolean mEncoderStarted;
    private MediaFormat mActualOutputFormat;

    private boolean mIsDecoderEOS;
    private boolean mIsEncoderEOS;
    private boolean mIsExtractorEOS;

    @SuppressWarnings("WeakerAccess")
    protected BaseTrackTranscoder(@NonNull MediaExtractor extractor,
                                  int trackIndex,
                                  @NonNull TranscoderMuxer muxer,
                                  @NonNull TrackType trackType) {
        mExtractor = extractor;
        mTrackIndex = trackIndex;
        mMuxer = muxer;
        mTrackType = trackType;
    }

    /**
     * Returns the encoder, if we have one at this point.
     * @return encoder or null
     */
    @Nullable
    @SuppressWarnings("WeakerAccess")
    protected MediaCodec getEncoder() {
        return mEncoder;
    }

    /**
     * Returns the decoder, if we have one at this point.
     * @return decoder or null
     */
    @Nullable
    @SuppressWarnings("unused")
    protected MediaCodec getDecoder() {
        return mDecoder;
    }

    @Override
    public final void setUp(@NonNull MediaFormat desiredOutputFormat) {
        mExtractor.selectTrack(mTrackIndex);
        try {
            mEncoder = MediaCodec.createEncoderByType(desiredOutputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        onConfigureEncoder(desiredOutputFormat, mEncoder);
        onStartEncoder(desiredOutputFormat, mEncoder);

        final MediaFormat inputFormat = mExtractor.getTrackFormat(mTrackIndex);
        try {
            mDecoder = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        onConfigureDecoder(inputFormat, mDecoder);
        onStartDecoder(inputFormat, mDecoder);
        onCodecsStarted(inputFormat, desiredOutputFormat, mDecoder, mEncoder);
    }

    /**
     * Wraps the configure operation on the encoder.
     * @param format output format
     * @param encoder encoder
     */
    @SuppressWarnings("WeakerAccess")
    protected void onConfigureEncoder(@NonNull MediaFormat format, @NonNull MediaCodec encoder) {
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
    }

    /**
     * Wraps the start operation on the encoder.
     * @param format output format
     * @param encoder encoder
     */
    @CallSuper
    protected void onStartEncoder(@NonNull MediaFormat format, @NonNull MediaCodec encoder) {
        encoder.start();
        mEncoderStarted = true;
        mEncoderBuffers = new MediaCodecBuffers(encoder);
    }

    /**
     * Wraps the configure operation on the decoder.
     * @param format input format
     * @param decoder decoder
     */
    protected void onConfigureDecoder(@NonNull MediaFormat format, @NonNull MediaCodec decoder) {
        decoder.configure(format, null, null, 0);
    }

    /**
     * Wraps the start operation on the decoder.
     * @param format input format
     * @param decoder decoder
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    @CallSuper
    protected void onStartDecoder(@NonNull MediaFormat format, @NonNull MediaCodec decoder) {
        decoder.start();
        mDecoderStarted = true;
        mDecoderBuffers = new MediaCodecBuffers(decoder);
    }

    /**
     * Called when both codecs have been started with the given formats.
     * @param inputFormat input format
     * @param outputFormat output format
     * @param decoder decoder
     * @param encoder encoder
     */
    protected void onCodecsStarted(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat,
                                   @NonNull MediaCodec decoder, @NonNull MediaCodec encoder) {
    }

    @Override
    public final long getLastWrittenPresentationTime() {
        return mWrittenPresentationTimeUs;
    }

    @Override
    public final boolean isFinished() {
        return mIsEncoderEOS;
    }

    @Override
    public void release() {
        if (mDecoder != null) {
            if (mDecoderStarted) {
                mDecoder.stop();
                mDecoderStarted = false;
            }
            mDecoder.release();
            mDecoder = null;
        }
        if (mEncoder != null) {
            if (mEncoderStarted) {
                mEncoder.stop();
                mEncoderStarted = false;
            }
            mEncoder.release();
            mEncoder = null;
        }
    }

    @Override
    public final boolean stepPipeline() {
        boolean busy = false;
        int status;
        while (drainEncoder(0) != DRAIN_STATE_NONE) busy = true;
        do {
            status = drainDecoder(0);
            if (status != DRAIN_STATE_NONE) busy = true;
            // NOTE: not repeating to keep from deadlock when encoder is full.
        } while (status == DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY);

        while (feedEncoder(0)) busy = true;
        while (feedDecoder(0) != DRAIN_STATE_NONE) busy = true;
        return busy;
    }

    /**
     * Called when the decoder has defined its actual output format.
     * @param format format
     */
    @CallSuper
    protected void onDecoderOutputFormatChanged(@NonNull MediaFormat format) {}

    /**
     * Called when the encoder has defined its actual output format.
     * @param format format
     */
    @CallSuper
    @SuppressWarnings("WeakerAccess")
    protected void onEncoderOutputFormatChanged(@NonNull MediaFormat format) {
        if (mActualOutputFormat != null) {
            throw new RuntimeException("Audio output format changed twice.");
        }
        mActualOutputFormat = format;
        mMuxer.setOutputFormat(mTrackType, mActualOutputFormat);
    }

    @SuppressWarnings("SameParameterValue")
    private int feedDecoder(long timeoutUs) {
        if (mIsExtractorEOS) return DRAIN_STATE_NONE;
        int trackIndex = mExtractor.getSampleTrackIndex();
        if (trackIndex >= 0 && trackIndex != mTrackIndex) {
            return DRAIN_STATE_NONE;
        }

        final int result = mDecoder.dequeueInputBuffer(timeoutUs);
        if (result < 0) return DRAIN_STATE_NONE;
        if (trackIndex < 0) {
            mIsExtractorEOS = true;
            mDecoder.queueInputBuffer(result, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return DRAIN_STATE_NONE;
        }

        final int sampleSize = mExtractor.readSampleData(mDecoderBuffers.getInputBuffer(result), 0);
        final boolean isKeyFrame = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        mDecoder.queueInputBuffer(result, 0, sampleSize, mExtractor.getSampleTime(), isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0);
        mExtractor.advance();
        return DRAIN_STATE_CONSUMED;
    }

    @SuppressWarnings("SameParameterValue")
    private boolean feedEncoder(long timeoutUs) {
        return onFeedEncoder(mEncoder, timeoutUs);
    }

    @SuppressWarnings("SameParameterValue")
    private int drainDecoder(long timeoutUs) {
        if (mIsDecoderEOS) return DRAIN_STATE_NONE;
        int result = mDecoder.dequeueOutputBuffer(mBufferInfo, timeoutUs);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return DRAIN_STATE_NONE;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                onDecoderOutputFormatChanged(mDecoder.getOutputFormat());
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }

        boolean isEos = (mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        boolean hasSize = mBufferInfo.size > 0;
        if (isEos) mIsDecoderEOS = true;
        if (isEos || hasSize) {
            onDrainDecoder(mDecoder, result, mBufferInfo.presentationTimeUs, isEos);
        }
        return DRAIN_STATE_CONSUMED;
    }

    @SuppressWarnings("SameParameterValue")
    private int drainEncoder(long timeoutUs) {
        if (mIsEncoderEOS) return DRAIN_STATE_NONE;

        int result = mEncoder.dequeueOutputBuffer(mBufferInfo, timeoutUs);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return DRAIN_STATE_NONE;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                onEncoderOutputFormatChanged(mEncoder.getOutputFormat());
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                mEncoderBuffers.onOutputBuffersChanged();
                return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }

        if (mActualOutputFormat == null) {
            throw new RuntimeException("Could not determine actual output format.");
        }

        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
            mIsEncoderEOS = true;
            mBufferInfo.set(0, 0, 0, mBufferInfo.flags);
        }
        if ((mBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            // SPS or PPS, which should be passed by MediaFormat.
            mEncoder.releaseOutputBuffer(result, false);
            return DRAIN_STATE_SHOULD_RETRY_IMMEDIATELY;
        }
        mMuxer.write(mTrackType, mEncoderBuffers.getOutputBuffer(result), mBufferInfo);
        mWrittenPresentationTimeUs = mBufferInfo.presentationTimeUs;
        mEncoder.releaseOutputBuffer(result, false);
        return DRAIN_STATE_CONSUMED;
    }

    /**
     * Called to drain the decoder. Implementors are required to call {@link MediaCodec#releaseOutputBuffer(int, boolean)}
     * with the given bufferIndex at some point.
     *
     * @param decoder the decoder
     * @param bufferIndex the buffer index to be released
     * @param presentationTimeUs frame timestamp
     * @param endOfStream whether we are in end of stream
     */
    protected abstract void onDrainDecoder(@NonNull MediaCodec decoder,
                                           int bufferIndex,
                                           long presentationTimeUs,
                                           boolean endOfStream);


    /**
     * Called to feed the encoder with processed data.
     * @param encoder the encoder
     * @param timeoutUs a timeout for this op
     * @return true if we want to keep working
     */
    protected abstract boolean onFeedEncoder(@NonNull MediaCodec encoder, long timeoutUs);
}
