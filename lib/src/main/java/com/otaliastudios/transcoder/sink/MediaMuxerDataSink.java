package com.otaliastudios.transcoder.sink;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.transcode.PassThroughTrackTranscoder;
import com.otaliastudios.transcoder.transcode.TrackTranscoder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MediaMuxerDataSink implements DataSink {

    /**
     * A queued sample is a sample that we haven't written yet because
     * the muxer is still being started (waiting for output formats).
     */
    private static class QueuedSample {
        private final TrackTranscoder mTranscoder;
        private final TrackType mType;
        private final int mSize;
        private final long mTimeUs;
        private final int mFlags;

        private QueuedSample(@NonNull TrackTranscoder transcoder,
                             @NonNull TrackType type,
                             @NonNull MediaCodec.BufferInfo bufferInfo) {
            mTranscoder = transcoder;
            mType = type;
            mSize = bufferInfo.size;
            mTimeUs = bufferInfo.presentationTimeUs;
            mFlags = bufferInfo.flags;
        }
    }

    private final static String TAG = MediaMuxerDataSink.class.getSimpleName();
    private final static Logger LOG = new Logger(TAG);

    // I have no idea whether this value is appropriate or not...
    private final static int BUFFER_SIZE = 64 * 1024;

    private boolean mMuxerStarted = false;
    private final MediaMuxer mMuxer;
    private final List<QueuedSample> mQueue = new ArrayList<>();
    private ByteBuffer mQueueBuffer;
    private Map<TrackType, TrackStatus> mStatus = new HashMap<>();
    private Map<TrackType, MediaFormat> mLastFormat = new HashMap<>();
    private Map<TrackType, Integer> mMuxerIndex = new HashMap<>();
    private final MediaMuxerChecks mMuxerChecks = new MediaMuxerChecks();

    public MediaMuxerDataSink(@NonNull String outputFilePath) {
        try {
            mMuxer = new MediaMuxer(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setOrientation(int rotation) {
        mMuxer.setOrientationHint(rotation);
    }

    @Override
    public void setLocation(double latitude, double longitude) {
        if (Build.VERSION.SDK_INT >= 19) {
            mMuxer.setLocation((float) latitude, (float) longitude);
        }
    }

    @Override
    public void setTrackStatus(@NonNull TrackType type, @NonNull TrackStatus status) {
        mStatus.put(type, status);
    }

    /**
     * Called by {@link com.otaliastudios.transcoder.transcode.TrackTranscoder}s
     * anytime the encoder output format changes (might actually be just once).
     */
    @Override
    public void setTrackOutputFormat(@NonNull TrackTranscoder transcoder,
                                     @NonNull TrackType type,
                                     @NonNull MediaFormat format) {
        //noinspection ConstantConditions
        boolean shouldValidate = mStatus.get(type).isTranscoding()
                && !(transcoder instanceof PassThroughTrackTranscoder);
        if (shouldValidate) {
            mMuxerChecks.checkOutputFormat(type, format);
        }
        mLastFormat.put(type, format);
        startIfNeeded();

    }

    private void startIfNeeded() {
        if (mMuxerStarted) return;
        //noinspection ConstantConditions
        boolean isTranscodingVideo = mStatus.get(TrackType.VIDEO).isTranscoding();
        //noinspection ConstantConditions
        boolean isTranscodingAudio = mStatus.get(TrackType.AUDIO).isTranscoding();
        MediaFormat videoOutputFormat = mLastFormat.get(TrackType.VIDEO);
        MediaFormat audioOutputFormat = mLastFormat.get(TrackType.AUDIO);
        boolean isVideoReady = videoOutputFormat != null || !isTranscodingVideo;
        boolean isAudioReady = audioOutputFormat != null || !isTranscodingAudio;
        if (!isVideoReady || !isAudioReady) return;

        // If both video and audio are ready, we can go on.
        // We will stop buffering data and we will start actually muxing it.
        if (isTranscodingVideo) {
            int videoIndex = mMuxer.addTrack(videoOutputFormat);
            mMuxerIndex.put(TrackType.VIDEO, videoIndex);
            LOG.v("Added track #" + videoIndex + " with " + videoOutputFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        if (isTranscodingAudio) {
            int audioIndex = mMuxer.addTrack(audioOutputFormat);
            mMuxerIndex.put(TrackType.AUDIO, audioIndex);
            LOG.v("Added track #" + audioIndex + " with " + audioOutputFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        mMuxer.start();
        mMuxerStarted = true;
        drainQueue();
    }

    @Override
    public void write(@NonNull TrackTranscoder transcoder, @NonNull TrackType type, @NonNull ByteBuffer byteBuffer, @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (mMuxerStarted) {
            //noinspection ConstantConditions
            mMuxer.writeSampleData(mMuxerIndex.get(type), byteBuffer, bufferInfo);
        } else {
            enqueue(transcoder, type, byteBuffer, bufferInfo);
        }
    }

    /**
     * Enqueues the given byffer by writing it into our own buffer and
     * just storing its position and size.
     *
     * @param transcoder transcoder
     * @param type track type
     * @param buffer input buffer
     * @param bufferInfo input buffer info
     */
    private void enqueue(@NonNull TrackTranscoder transcoder,
                         @NonNull TrackType type,
                         @NonNull ByteBuffer buffer,
                         @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (mQueueBuffer == null) {
            mQueueBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
        }
        buffer.limit(bufferInfo.offset + bufferInfo.size);
        buffer.position(bufferInfo.offset);
        mQueueBuffer.put(buffer);
        mQueue.add(new QueuedSample(transcoder, type, bufferInfo));
    }

    /**
     * Writes all enqueued samples into the muxer, now that it is
     * open and running.
     */
    private void drainQueue() {
        if (mQueue.isEmpty()) return;
        mQueueBuffer.flip();
        LOG.i("Output format determined, writing pending data into the muxer. "
                + "samples:" + mQueue.size() + " "
                + "bytes:" + mQueueBuffer.limit());
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int offset = 0;
        for (QueuedSample sample : mQueue) {
            bufferInfo.set(offset, sample.mSize, sample.mTimeUs, sample.mFlags);
            write(sample.mTranscoder, sample.mType, mQueueBuffer, bufferInfo);
            offset += sample.mSize;
        }
        mQueue.clear();
        mQueueBuffer = null;
    }

    @Override
    public void stop() {
        mMuxer.stop(); // If this fails, let's throw.
    }

    @Override
    public void release() {
        try {
            mMuxer.release();
        } catch (Exception e) {
            LOG.w("Failed to release the muxer.", e);
        }
    }
}
