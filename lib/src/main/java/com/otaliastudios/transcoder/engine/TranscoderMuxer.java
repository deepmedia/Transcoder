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
    private final MediaMuxer mMuxer;
    private final Listener mListener;
    private final TracksInfo mInfo;

    private MediaFormat mVideoFormat;
    private MediaFormat mAudioFormat;
    private int mVideoTrackIndex;
    private int mAudioTrackIndex;
    private ByteBuffer mByteBuffer;
    private final List<SampleInfo> mSampleInfoList;
    private boolean mMuxerStarted;

    TranscoderMuxer(@NonNull MediaMuxer muxer, @NonNull TracksInfo info, @NonNull Listener listener) {
        mMuxer = muxer;
        mInfo = info;
        mListener = listener;
        mSampleInfoList = new ArrayList<>();
    }

    /**
     * Called by {@link com.otaliastudios.transcoder.transcode.TrackTranscoder}s
     * anytime the encoder output format changes (might actually be just once).
     *
     * @param sampleType the sample type, either audio or video
     * @param format the new format
     */
    public void setOutputFormat(@NonNull SampleType sampleType, @NonNull MediaFormat format) {
        switch (sampleType) {
            case VIDEO:
                mVideoFormat = format;
                break;
            case AUDIO:
                mAudioFormat = format;
                break;
            default:
                throw new AssertionError();
        }
        onSetOutputFormat();
    }

    private void onSetOutputFormat() {
        boolean isTranscodingVideo = mInfo.videoTrackStatus.isTranscoding();
        boolean isTranscodingAudio = mInfo.audioTrackStatus.isTranscoding();
        boolean isVideoReady = mVideoFormat != null || !isTranscodingVideo;
        boolean isAudioReady = mAudioFormat != null || !isTranscodingAudio;
        if (!isVideoReady || !isAudioReady) return;
        if (mMuxerStarted) return;

        // If both video and audio are ready, notify the listener and go on.
        // We will stop buffering data and we will start actually muxing it.
        mListener.onDetermineOutputFormat();
        if (isTranscodingVideo) {
            mVideoTrackIndex = mMuxer.addTrack(mVideoFormat);
            LOG.v("Added track #" + mVideoTrackIndex + " with " + mVideoFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        if (isTranscodingAudio) {
            mAudioTrackIndex = mMuxer.addTrack(mAudioFormat);
            LOG.v("Added track #" + mAudioTrackIndex + " with " + mAudioFormat.getString(MediaFormat.KEY_MIME) + " to muxer");
        }
        mMuxer.start();
        mMuxerStarted = true;
        if (mSampleInfoList.size() > 0) {
            // Write pending data.
            mByteBuffer.flip();
            LOG.v("Output format determined, writing " + mSampleInfoList.size()
                    + " samples / " + mByteBuffer.limit() + " bytes to muxer.");
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int offset = 0;
            for (SampleInfo sampleInfo : mSampleInfoList) {
                sampleInfo.writeToBufferInfo(bufferInfo, offset);
                writeSampleData(sampleInfo.mSampleType, mByteBuffer, bufferInfo);
                offset += sampleInfo.mSize;
            }
            mSampleInfoList.clear();
            mByteBuffer = null;
        }
    }

    public void writeSampleData(@NonNull SampleType sampleType, @NonNull ByteBuffer byteBuf, @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (mMuxerStarted) {
            mMuxer.writeSampleData(getTrackIndexForSampleType(sampleType), byteBuf, bufferInfo);
        } else {
            // Write to our own buffer.
            byteBuf.limit(bufferInfo.offset + bufferInfo.size);
            byteBuf.position(bufferInfo.offset);
            if (mByteBuffer == null) {
                mByteBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
            }
            mByteBuffer.put(byteBuf);
            mSampleInfoList.add(new SampleInfo(sampleType, bufferInfo.size, bufferInfo));
        }
    }

    private int getTrackIndexForSampleType(@NonNull SampleType sampleType) {
        switch (sampleType) {
            case VIDEO:
                return mVideoTrackIndex;
            case AUDIO:
                return mAudioTrackIndex;
            default:
                throw new AssertionError();
        }
    }

    public enum SampleType {
        VIDEO, AUDIO
    }

    private static class SampleInfo {
        private final SampleType mSampleType;
        private final int mSize;
        private final long mPresentationTimeUs;
        private final int mFlags;

        private SampleInfo(@NonNull SampleType sampleType, int size, @NonNull MediaCodec.BufferInfo bufferInfo) {
            mSampleType = sampleType;
            mSize = size;
            mPresentationTimeUs = bufferInfo.presentationTimeUs;
            mFlags = bufferInfo.flags;
        }

        private void writeToBufferInfo(@NonNull MediaCodec.BufferInfo bufferInfo, int offset) {
            bufferInfo.set(offset, mSize, mPresentationTimeUs, mFlags);
        }
    }

    public interface Listener {
        void onDetermineOutputFormat();
    }
}
