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

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.TrackType;
import com.otaliastudios.transcoder.sink.DataSink;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.time.TimeInterpolator;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PassThroughTrackTranscoder implements TrackTranscoder {

    private final DataSource mDataSource;
    private final DataSink mDataSink;
    private final DataSource.Chunk mDataChunk;
    private final TrackType mTrackType;
    private final MediaCodec.BufferInfo mBufferInfo = new MediaCodec.BufferInfo();
    private boolean mIsEOS;
    private final MediaFormat mOutputFormat;
    private boolean mOutputFormatSet = false;
    private final TimeInterpolator mTimeInterpolator;

    public PassThroughTrackTranscoder(@NonNull DataSource dataSource,
                                      @NonNull DataSink dataSink,
                                      @NonNull TrackType trackType,
                                      @NonNull TimeInterpolator timeInterpolator) {
        mDataSource = dataSource;
        mDataSink = dataSink;
        mTrackType = trackType;
        mOutputFormat = dataSource.getTrackFormat(trackType);
        if (mOutputFormat == null) {
            throw new IllegalArgumentException("Output format is null!");
        }
        int bufferSize = mOutputFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        mDataChunk = new DataSource.Chunk();
        mDataChunk.buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
        mTimeInterpolator = timeInterpolator;
    }

    @Override
    public void setUp(@NonNull MediaFormat desiredOutputFormat) { }

    @Override
    public boolean transcode(boolean forceInputEos) {
        if (mIsEOS) return false;
        if (!mOutputFormatSet) {
            mDataSink.setTrackFormat(mTrackType, mOutputFormat);
            mOutputFormatSet = true;
        }
        if (mDataSource.isDrained() || forceInputEos) {
            mDataChunk.buffer.clear();
            mBufferInfo.set(0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            mDataSink.writeTrack(mTrackType, mDataChunk.buffer, mBufferInfo);
            mIsEOS = true;
            return true;
        }
        if (!mDataSource.canReadTrack(mTrackType)) {
            return false;
        }

        mDataChunk.buffer.clear();
        mDataSource.readTrack(mDataChunk);
        long timestampUs = mTimeInterpolator.interpolate(mTrackType, mDataChunk.timestampUs);
        int flags = mDataChunk.isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0;
        mBufferInfo.set(0, mDataChunk.bytes, timestampUs, flags);
        mDataSink.writeTrack(mTrackType, mDataChunk.buffer, mBufferInfo);
        return true;
    }

    @Override
    public boolean isFinished() {
        return mIsEOS;
    }

    @Override
    public void tearDown() {
    }
}
