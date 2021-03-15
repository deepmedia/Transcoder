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
import com.otaliastudios.transcoder.internal.media.MediaCodecBuffers;
import com.otaliastudios.transcoder.sink.DataSink;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.time.TimeInterpolator;
import com.otaliastudios.transcoder.internal.video.FrameDrawer;
import com.otaliastudios.transcoder.transcode.internal.FramePublisher;
import com.otaliastudios.transcoder.internal.media.MediaFormatConstants;
import com.otaliastudios.transcoder.transcode.internal.FrameDropper;

import java.nio.ByteBuffer;

// Refer: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java
public class VideoTrackTranscoder extends BaseTrackTranscoder {

    private FrameDrawer mDecoderOutputSurface;
    private FramePublisher mEncoderInputSurface;
    private MediaCodec mEncoder; // Keep this since we want to signal EOS on it.
    private FrameDropper mFrameDropper;
    private final TimeInterpolator mTimeInterpolator;
    private final int mSourceRotation;
    private final int mExtraRotation;

    public VideoTrackTranscoder(
            @NonNull DataSource dataSource,
            @NonNull DataSink dataSink,
            @NonNull TimeInterpolator timeInterpolator,
            int rotation) {
        super(dataSource, dataSink, TrackType.VIDEO);
        mTimeInterpolator = timeInterpolator;
        mSourceRotation = dataSource.getOrientation();
        mExtraRotation = rotation;
    }

    @Override
    protected void onConfigureEncoder(@NonNull MediaFormat format, @NonNull MediaCodec encoder) {
        // Flip the width and height as needed. This means rotating the VideoStrategy rotation
        // by the amount that was set in the TranscoderOptions.
        // It is possible that the format has its own KEY_ROTATION, but we don't care, that will
        // be respected at playback time.
        int width = format.getInteger(MediaFormat.KEY_WIDTH);
        int height = format.getInteger(MediaFormat.KEY_HEIGHT);
        boolean flip = (mExtraRotation % 180) != 0;
        format.setInteger(MediaFormat.KEY_WIDTH, flip ? height : width);
        format.setInteger(MediaFormat.KEY_HEIGHT, flip ? width : height);
        super.onConfigureEncoder(format, encoder);
    }

    @Override
    protected void onStartEncoder(@NonNull MediaFormat format, @NonNull MediaCodec encoder) {
        mEncoderInputSurface = new FramePublisher(encoder.createInputSurface());
        super.onStartEncoder(format, encoder);
    }

    @Override
    protected void onConfigureDecoder(@NonNull MediaFormat format, @NonNull MediaCodec decoder) {
        // Just a sanity check that the rotation coming from DataSource is not different from
        // the one found in the DataSource's MediaFormat for video.
        int sourceRotation = 0;
        if (format.containsKey(MediaFormatConstants.KEY_ROTATION_DEGREES)) {
            sourceRotation = format.getInteger(MediaFormatConstants.KEY_ROTATION_DEGREES);
        }
        if (sourceRotation != mSourceRotation) {
            throw new RuntimeException("Unexpected difference in rotation." +
                    " DataSource:" + mSourceRotation +
                    " MediaFormat:" + sourceRotation);
        }

        // Decoded video is rotated automatically in Android 5.0 lollipop. Turn off here because we don't want to encode rotated one.
        // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
        format.setInteger(MediaFormatConstants.KEY_ROTATION_DEGREES, 0);

        // The rotation we should apply is the intrinsic source rotation, plus any extra
        // rotation that was set into the TranscoderOptions.
        mDecoderOutputSurface = new FrameDrawer();
        mDecoderOutputSurface.setRotation((mSourceRotation + mExtraRotation) % 360);
        decoder.configure(format, mDecoderOutputSurface.getSurface(), null, 0);
    }

    @Override
    protected void onCodecsStarted(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat, @NonNull MediaCodec decoder, @NonNull MediaCodec encoder) {
        super.onCodecsStarted(inputFormat, outputFormat, decoder, encoder);
        mFrameDropper = FrameDropper.newDropper(
                inputFormat.getInteger(MediaFormat.KEY_FRAME_RATE),
                outputFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
        mEncoder = encoder;

        // Cropping support.
        // Ignoring any outputFormat KEY_ROTATION (which is applied at playback time), the rotation
        // difference between input and output is mSourceRotation + mExtraRotation.
        int rotation = (mSourceRotation + mExtraRotation) % 360;
        boolean flip = (rotation % 180) != 0;
        float inputWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        float inputHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        float inputRatio = inputWidth / inputHeight;
        float outputWidth = flip ? outputFormat.getInteger(MediaFormat.KEY_HEIGHT) : outputFormat.getInteger(MediaFormat.KEY_WIDTH);
        float outputHeight = flip ? outputFormat.getInteger(MediaFormat.KEY_WIDTH) : outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
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
    public void tearDown() {
        if (mDecoderOutputSurface != null) {
            mDecoderOutputSurface.release();
            mDecoderOutputSurface = null;
        }
        if (mEncoderInputSurface != null) {
            mEncoderInputSurface.release();
            mEncoderInputSurface = null;
        }
        super.tearDown();
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
