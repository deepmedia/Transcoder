/*
 * Copyright (C) 2014 Yuya Tanaka
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
package com.otaliastudios.transcoder.transcode;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.engine.TranscoderMuxer;
import com.otaliastudios.transcoder.time.TimeInterpolator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PassThroughTrackTranscoder implements TrackTranscoder {
    private final MediaExtractor mExtractor;
    private final int mTrackIndex;
    private final TranscoderMuxer mMuxer;
    private final TrackType mTrackType;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private int mBufferSize;
    private ByteBuffer mBuffer;
    private boolean mIsEOS;

    private long mLastPresentationTime;

    private final MediaFormat mOutputFormat;
    private boolean mOutputFormatSet = false;

    private TimeInterpolator mTimeInterpolator;

    public PassThroughTrackTranscoder(@NonNull MediaExtractor extractor,
                                      int trackIndex,
                                      @NonNull TranscoderMuxer muxer,
                                      @NonNull TrackType trackType,
                                      @NonNull TimeInterpolator timeInterpolator) {
        mExtractor = extractor;
        mTrackIndex = trackIndex;
        mMuxer = muxer;
        mTrackType = trackType;

        mOutputFormat = mExtractor.getTrackFormat(mTrackIndex);
        mBufferSize = mOutputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        mBuffer = ByteBuffer.allocateDirect(mBufferSize).order(ByteOrder.nativeOrder());

        mTimeInterpolator = timeInterpolator;
    }

    @Override
    public void setUp(@NonNull MediaFormat desiredOutputFormat) { }

    @SuppressLint("Assert")
    @Override
    public boolean transcode() {
        if (mIsEOS) return false;
        if (!mOutputFormatSet) {
            mMuxer.setOutputFormat(mTrackType, mOutputFormat);
            mOutputFormatSet = true;
        }
        int trackIndex = mExtractor.getSampleTrackIndex();
        if (trackIndex < 0) {
            mBuffer.clear();
            mBufferInfo.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mMuxer.write(mTrackType, mBuffer, mBufferInfo);
            mIsEOS = true;
            return true;
        }
        if (trackIndex != mTrackIndex) return false;

        mBuffer.clear();
        int sampleSize = mExtractor.readSampleData(mBuffer, 0);
        assert sampleSize <= mBufferSize;
        boolean isKeyFrame = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        int flags = isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0;
        long realTimestampUs = mExtractor.getSampleTime();
        long timestampUs = mTimeInterpolator.interpolate(mTrackType, realTimestampUs);
        mBufferInfo.set(0, sampleSize, timestampUs, flags);
        mMuxer.write(mTrackType, mBuffer, mBufferInfo);
        mLastPresentationTime = realTimestampUs;

        mExtractor.advance();
        return true;
    }

    @Override
    public long getLastPresentationTime() {
        return mLastPresentationTime;
    }

    @Override
    public boolean isFinished() {
        return mIsEOS;
    }

    @Override
    public void release() {
    }
}
