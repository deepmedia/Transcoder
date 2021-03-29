package com.otaliastudios.transcoder.sink;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import com.otaliastudios.transcoder.common.TrackStatus;
import com.otaliastudios.transcoder.common.TrackType;
import com.otaliastudios.transcoder.internal.utils.MutableTrackMap;
import com.otaliastudios.transcoder.internal.utils.Logger;

import java.io.FileDescriptor;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static com.otaliastudios.transcoder.internal.utils.TrackMapKt.mutableTrackMapOf;


/**
 * A {@link DataSink} implementation that:
 *
 * - Uses {@link MediaMuxer} to collect data
 * - Creates an output file with the readable media
 */
public class DefaultDataSink implements DataSink {

    /**
     * A queued sample is a sample that we haven't written yet because
     * the muxer is still being started (waiting for output formats).
     */
    private static class QueuedSample {
        private final TrackType mType;
        private final int mSize;
        private final long mTimeUs;
        private final int mFlags;

        private QueuedSample(@NonNull TrackType type,
                             @NonNull MediaCodec.BufferInfo bufferInfo) {
            mType = type;
            mSize = bufferInfo.size;
            mTimeUs = bufferInfo.presentationTimeUs;
            mFlags = bufferInfo.flags;
        }
    }

    private final static Logger LOG = new Logger("DefaultDataSink");

    // We must be able to handle potentially big buffers (e.g. first keyframe) in the queue.
    // Got crashes with 152kb - let's use 256kb. TODO use a dynamic queue instead
    private final static int BUFFER_SIZE = 256 * 1024;

    private boolean mMuxerStarted = false;
    private final MediaMuxer mMuxer;
    private final List<QueuedSample> mQueue = new ArrayList<>();
    private ByteBuffer mQueueBuffer;
    private final MutableTrackMap<TrackStatus> mStatus = mutableTrackMapOf(null);
    private final MutableTrackMap<MediaFormat> mLastFormat = mutableTrackMapOf(null);
    private final MutableTrackMap<Integer> mMuxerIndex = mutableTrackMapOf(null);
    private final DefaultDataSinkChecks mMuxerChecks = new DefaultDataSinkChecks();

    public DefaultDataSink(@NonNull String outputFilePath) {
        this(outputFilePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @SuppressWarnings("WeakerAccess")
    public DefaultDataSink(@NonNull String outputFilePath, int format) {
        try {
            mMuxer = new MediaMuxer(outputFilePath, format);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public DefaultDataSink(@NonNull FileDescriptor fileDescriptor) {
        this(fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressWarnings("WeakerAccess")
    public DefaultDataSink(@NonNull FileDescriptor fileDescriptor, int format) {
        try {
            mMuxer = new MediaMuxer(fileDescriptor, format);
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
        mStatus.set(type, status);
    }

    @Override
    public void setTrackFormat(@NonNull TrackType type, @NonNull MediaFormat format) {
        LOG.i("setTrackFormat(" + type + ") format=" + format);
        boolean shouldValidate = mStatus.get(type) == TrackStatus.COMPRESSING;
        if (shouldValidate) {
            mMuxerChecks.checkOutputFormat(type, format);
        }
        mLastFormat.set(type, format);
        maybeStart();
    }

    private void maybeStart() {
        if (mMuxerStarted) return;
        boolean isTranscodingVideo = mStatus.get(TrackType.VIDEO).isTranscoding();
        boolean isTranscodingAudio = mStatus.get(TrackType.AUDIO).isTranscoding();
        MediaFormat videoOutputFormat = mLastFormat.getOrNull(TrackType.VIDEO);
        MediaFormat audioOutputFormat = mLastFormat.getOrNull(TrackType.AUDIO);
        boolean isVideoReady = videoOutputFormat != null || !isTranscodingVideo;
        boolean isAudioReady = audioOutputFormat != null || !isTranscodingAudio;
        if (!isVideoReady || !isAudioReady) return;

        // If both video and audio are ready, we can go on.
        // We will stop buffering data and we will start actually muxing it.
        if (isTranscodingVideo) {
            int videoIndex = mMuxer.addTrack(videoOutputFormat);
            mMuxerIndex.setVideo(videoIndex);
            LOG.v("Added track #" + videoIndex + " with " + videoOutputFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        if (isTranscodingAudio) {
            int audioIndex = mMuxer.addTrack(audioOutputFormat);
            mMuxerIndex.setAudio(audioIndex);
            LOG.v("Added track #" + audioIndex + " with " + audioOutputFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        mMuxer.start();
        mMuxerStarted = true;
        drainQueue();
    }

    @Override
    public void writeTrack(@NonNull TrackType type, @NonNull ByteBuffer byteBuffer, @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (mMuxerStarted) {
            /* LOG.v("writeTrack(" + type + "): offset=" + bufferInfo.offset
                    + "\trealOffset=" + byteBuffer.position()
                    + "\tsize=" + bufferInfo.size
                    + "\trealSize=" + byteBuffer.remaining()
                    + "\ttime=" + bufferInfo.presentationTimeUs
                    + "\tflags=" + bufferInfo.flags
                    + "\teos=" + ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            );
             */
            mMuxer.writeSampleData(mMuxerIndex.get(type), byteBuffer, bufferInfo);
        } else {
            enqueue(type, byteBuffer, bufferInfo);
        }
    }

    /**
     * Enqueues the given byffer by writing it into our own buffer and
     * just storing its position and size.
     *
     * @param type track type
     * @param buffer input buffer
     * @param bufferInfo input buffer info
     */
    private void enqueue(@NonNull TrackType type,
                         @NonNull ByteBuffer buffer,
                         @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (mQueueBuffer == null) {
            mQueueBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
        }
        LOG.v("enqueue(" + type + "): offset=" + bufferInfo.offset
                + "\trealOffset=" + buffer.position()
                + "\tsize=" + bufferInfo.size
                + "\trealSize=" + buffer.remaining()
                + "\tavailable=" + mQueueBuffer.remaining()
                + "\ttotal=" + BUFFER_SIZE);
        buffer.limit(bufferInfo.offset + bufferInfo.size);
        buffer.position(bufferInfo.offset);
        mQueueBuffer.put(buffer);
        mQueue.add(new QueuedSample(type, bufferInfo));
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
            writeTrack(sample.mType, mQueueBuffer, bufferInfo);
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
