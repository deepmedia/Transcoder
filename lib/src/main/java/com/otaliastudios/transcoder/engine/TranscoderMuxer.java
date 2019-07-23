/*
 * Copyright (C) 2015 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.otaliastudios.transcoder.engine;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.time.TimeInterpolator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

/**
 * This class queues until all output track formats are determined.
 */
public class TranscoderMuxer {

    private static final String TAG = TranscoderMuxer.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    private static final int BUFFER_SIZE = 64 * 1024; // I have no idea whether this value is appropriate or not...

    private static class QueuedSample {
        private final TrackType mTrackType;
        private final int mSize;
        private final long mPresentationTimeUs;
        private final int mFlags;

        private QueuedSample(@NonNull TrackType trackType, @NonNull MediaCodec.BufferInfo bufferInfo) {
            mTrackType = trackType;
            mSize = bufferInfo.size;
            mPresentationTimeUs = bufferInfo.presentationTimeUs;
            mFlags = bufferInfo.flags;
        }

        private void toBufferInfo(@NonNull MediaCodec.BufferInfo bufferInfo, int offset) {
            bufferInfo.set(offset, mSize, mPresentationTimeUs, mFlags);
        }
    }

    private final MediaMuxer mMuxer;
    private final Tracks mTracks;
    private boolean mMuxerStarted;
    private final List<QueuedSample> mQueue = new ArrayList<>();
    private ByteBuffer mQueueBuffer;
    private TimeInterpolator mTimeInterpolator;

    TranscoderMuxer(@NonNull MediaMuxer muxer, @NonNull Tracks info, @NonNull TimeInterpolator timeInterpolator) {
        mMuxer = muxer;
        mTracks = info;
        mTimeInterpolator = timeInterpolator;
    }

    /**
     * Called by {@link com.otaliastudios.transcoder.transcode.TrackTranscoder}s
     * anytime the encoder output format changes (might actually be just once).
     *
     * @param trackType the sample type, either audio or video
     * @param format the new format
     */
    public void setOutputFormat(@NonNull TrackType trackType, @NonNull MediaFormat format) {
        mTracks.outputFormat(trackType, format);

        // If we have both, go on.
        boolean isTranscodingVideo = mTracks.status(TrackType.VIDEO).isTranscoding();
        boolean isTranscodingAudio = mTracks.status(TrackType.AUDIO).isTranscoding();
        MediaFormat videoOutputFormat = mTracks.outputFormat(TrackType.VIDEO);
        MediaFormat audioOutputFormat = mTracks.outputFormat(TrackType.AUDIO);
        boolean isVideoReady = videoOutputFormat != null || !isTranscodingVideo;
        boolean isAudioReady = audioOutputFormat != null || !isTranscodingAudio;
        if (!isVideoReady || !isAudioReady) return;
        if (mMuxerStarted) return;

        // If both video and audio are ready, validate the formats and go on.
        // We will stop buffering data and we will start actually muxing it.
        MediaFormatValidator formatValidator = new MediaFormatValidator();
        formatValidator.validateVideoOutputFormat(videoOutputFormat);
        formatValidator.validateAudioOutputFormat(audioOutputFormat);

        if (isTranscodingVideo) {
            int videoIndex = mMuxer.addTrack(videoOutputFormat);
            mTracks.outputIndex(TrackType.VIDEO, videoIndex);
            LOG.v("Added track #" + videoIndex + " with " + videoOutputFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        if (isTranscodingAudio) {
            int audioIndex = mMuxer.addTrack(audioOutputFormat);
            mTracks.outputIndex(TrackType.AUDIO, audioIndex);
            LOG.v("Added track #" + audioIndex + " with " + audioOutputFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        mMuxer.start();
        mMuxerStarted = true;
        drainQueue();
    }

    @SuppressWarnings("StatementWithEmptyBody")
    public void write(@NonNull TrackType type, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (mMuxerStarted) {
            boolean isEos = (bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
            if (isEos && bufferInfo.presentationTimeUs == 0) {
                // Do not pass this to the interpolator, it needs increasing timestamps,
                // this is not a real buffer, just a signal.
            } else {
                bufferInfo.presentationTimeUs = mTimeInterpolator.interpolate(type, bufferInfo.presentationTimeUs);
            }
            mMuxer.writeSampleData(mTracks.outputIndex(type), byteBuf, bufferInfo);
        } else {
            enqueue(type, byteBuf, bufferInfo);
        }
    }

    /**
     * Enqueues the given byffer by writing it into our own buffer and
     * just storing its position and size.
     * @param type sample type
     * @param buffer input buffer
     * @param bufferInfo input buffer info
     */
    private void enqueue(@NonNull TrackType type, @NonNull ByteBuffer buffer, @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (mQueueBuffer == null) {
            mQueueBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
        }
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
            sample.toBufferInfo(bufferInfo, offset);
            write(sample.mTrackType, mQueueBuffer, bufferInfo);
            offset += sample.mSize;
        }
        mQueue.clear();
        mQueueBuffer = null;
    }
}
