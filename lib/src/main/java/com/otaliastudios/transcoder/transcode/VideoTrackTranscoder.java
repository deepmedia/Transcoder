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
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.MediaCodecBuffers;
import com.otaliastudios.transcoder.sink.DataSink;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.time.TimeInterpolator;
import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;
import com.otaliastudios.transcoder.transcode.internal.VideoEncoderInput;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.MediaFormatConstants;
import com.otaliastudios.transcoder.transcode.internal.VideoFrameDropper;

import java.nio.ByteBuffer;

// Refer: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java
public class VideoTrackTranscoder extends BaseTrackTranscoder {

    private static final String TAG = VideoTrackTranscoder.class.getSimpleName();
    @SuppressWarnings("unused")
    private static final Logger LOG = new Logger(TAG);

    private VideoDecoderOutput mDecoderOutputSurface;
    private VideoEncoderInput mEncoderInputSurface;
    private MediaCodec mEncoder; // Keep this since we want to signal EOS on it.
    private VideoFrameDropper mFrameDropper;
    private final TimeInterpolator mTimeInterpolator;
    private final int mRotation;
    private final boolean mFlip;

    public VideoTrackTranscoder(
            @NonNull DataSource dataSource,
            @NonNull DataSink dataSink,
            @NonNull TimeInterpolator timeInterpolator,
            int rotation) {
        super(dataSource, dataSink, TrackType.VIDEO);
        mTimeInterpolator = timeInterpolator;
        mRotation = (rotation + dataSource.getOrientation()) % 360;
        mFlip = mRotation % 180 != 0;
    }

    @Override
    protected void onConfigureEncoder(@NonNull MediaFormat format, @NonNull MediaCodec encoder) {
        // Flip the width and height as needed.
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        format.setInteger(MediaFormat.KEY_WIDTH, mFlip ? height : width);
        format.setInteger(MediaFormat.KEY_HEIGHT, mFlip ? width : height);
        super.onConfigureEncoder(format, encoder);
    }

    @Override
    protected void onStartEncoder(@NonNull MediaFormat format, @NonNull MediaCodec encoder) {
        mEncoderInputSurface = new VideoEncoderInput(encoder.createInputSurface());
        super.onStartEncoder(format, encoder);
    }

    @Override
    protected void onConfigureDecoder(@NonNull MediaFormat format, @NonNull MediaCodec decoder) {
        if (format.containsKey(MediaFormatConstants.KEY_ROTATION_DEGREES)) {
            // Decoded video is rotated automatically in Android 5.0 lollipop. Turn off here because we don't want to encode rotated one.
            // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
            format.setInteger(MediaFormatConstants.KEY_ROTATION_DEGREES, 0);
        }
        mDecoderOutputSurface = new VideoDecoderOutput();
        mDecoderOutputSurface.setRotation(mRotation);
        decoder.configure(format, mDecoderOutputSurface.getSurface(), null, 0);
    }

    @Override
    protected void onCodecsStarted(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat, @NonNull MediaCodec decoder, @NonNull MediaCodec encoder) {
        super.onCodecsStarted(inputFormat, outputFormat, decoder, encoder);
        mFrameDropper = VideoFrameDropper.newDropper(
                inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE),
                outputFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
        mEncoder = encoder;

        // Cropping support.
        float inputWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        float inputHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        float inputRatio = inputWidth / inputHeight;
        float outputWidth = mFlip ? outputFormat.getInteger(MediaFormat.KEY_HEIGHT) : outputFormat.getInteger(MediaFormat.KEY_WIDTH);
        float outputHeight = mFlip ? outputFormat.getInteger(MediaFormat.KEY_WIDTH) : outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        float outputRatio = outputWidth / outputHeight;
        float scaleX = 1, scaleY = 1;
        if (inputRatio > outputRatio) { // Input wider. We have a scaleX.
            scaleX = inputRatio / outputRatio;
        } else if (inputRatio < outputRatio) { // Input taller. We have a scaleY.
            scaleY = outputRatio / inputRatio;
        }
        mDecoderOutputSurface.setScale(scaleX, scaleY);
    }

    @Override
    public void release() {
        if (mDecoderOutputSurface != null) {
            mDecoderOutputSurface.release();
            mDecoderOutputSurface = null;
        }
        if (mEncoderInputSurface != null) {
            mEncoderInputSurface.release();
            mEncoderInputSurface = null;
        }
        super.release();
        mEncoder = null;
    }

    @Override
    protected boolean onFeedEncoder(@NonNull MediaCodec encoder, @NonNull MediaCodecBuffers encoderBuffers, long timeoutUs) {
        // We do not feed the encoder, instead we wait for the encoder surface onFrameAvailable callback.
        return false;
    }

    @Override
    protected void onDrainDecoder(@NonNull MediaCodec decoder, int bufferIndex, @NonNull ByteBuffer bufferData, long presentationTimeUs, boolean endOfStream) {
        if (endOfStream) {
            mEncoder.signalEndOfInputStream();
            decoder.releaseOutputBuffer(bufferIndex, false);
        } else {
            long interpolatedTimeUs = mTimeInterpolator.interpolate(TrackType.VIDEO, presentationTimeUs);
            if (mFrameDropper.shouldRenderFrame(interpolatedTimeUs)) {
                decoder.releaseOutputBuffer(bufferIndex, true);
                mDecoderOutputSurface.drawFrame();
                mEncoderInputSurface.onFrame(interpolatedTimeUs);
            } else {
                decoder.releaseOutputBuffer(bufferIndex, false);
            }
        }
    }
}
