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
import com.otaliastudios.transcoder.engine.TranscoderMuxer;
import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;
import com.otaliastudios.transcoder.transcode.internal.VideoEncoderInput;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.MediaFormatConstants;

// Refer: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/ExtractDecodeEditEncodeMuxTest.java
public class VideoTrackTranscoder extends BaseTrackTranscoder {

    private static final String TAG = VideoTrackTranscoder.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    private VideoDecoderOutput mDecoderOutputSurface;
    private VideoEncoderInput mEncoderInputSurface;
    private MediaCodec mEncoder; // Keep this since we want to signal EOS on it.

    private double mInFrameRateReciprocal;
    private double mOutFrameRateReciprocal;
    private double mFrameRateReciprocalSum;

    public VideoTrackTranscoder(
            @NonNull MediaExtractor extractor,
            int trackIndex,
            @NonNull TranscoderMuxer muxer) {
        super(extractor, trackIndex, muxer, TrackType.VIDEO);
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
        decoder.configure(format, mDecoderOutputSurface.getSurface(), null, 0);
    }

    @Override
    protected void onCodecsStarted(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat, @NonNull MediaCodec decoder, @NonNull MediaCodec encoder) {
        super.onCodecsStarted(inputFormat, outputFormat, decoder, encoder);
        mEncoder = encoder;

        // Cropping support.
        float inputWidth = inputFormat.getInteger(MediaFormat.KEY_WIDTH);
        float inputHeight = inputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        float inputRatio = inputWidth / inputHeight;
        float outputWidth = outputFormat.getInteger(MediaFormat.KEY_WIDTH);
        float outputHeight = outputFormat.getInteger(MediaFormat.KEY_HEIGHT);
        float outputRatio = outputWidth / outputHeight;
        float scaleX = 1, scaleY = 1;
        if (inputRatio > outputRatio) { // Input wider. We have a scaleX.
            scaleX = inputRatio / outputRatio;
        } else if (inputRatio < outputRatio) { // Input taller. We have a scaleY.
            scaleY = outputRatio / inputRatio;
        }
        // I don't think we should consider rotation and flip these - we operate on non-rotated
        // surfaces and pass the input rotation metadata to the output muxer, see TranscoderEngine.setupMetadata.
        mDecoderOutputSurface.setScale(scaleX, scaleY);

        // Frame dropping support.
        int frameRate = outputFormat.getInteger(MediaFormat.KEY_FRAME_RATE);
        mTargetAvgStep = (1F / frameRate) * 1000 * 1000;
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
    protected boolean onFeedEncoder(@NonNull MediaCodec encoder, long timeoutUs) {
        // We do not feed the encoder, see below.
        return false;
    }

    @Override
    protected void onDrainDecoder(@NonNull MediaCodec decoder, int bufferIndex, long presentationTimeUs, boolean endOfStream) {
        if (endOfStream) {
            mEncoder.signalEndOfInputStream();
            decoder.releaseOutputBuffer(bufferIndex, false);
        } else if (shouldRenderFrame(presentationTimeUs)) {
            decoder.releaseOutputBuffer(bufferIndex, true);
            mDecoderOutputSurface.drawFrame();
            mEncoderInputSurface.onFrame(presentationTimeUs);
        }
    }

    // TODO improve this. as it is now, rendering a frame after dropping many,
    // will not decrease avgStep but rather increase it (for this single frame; then it starts decreasing).
    // This has the effect that, when a frame is rendered, the following frame is always rendered,
    // because the conditions are worse then before. After this second frame things go back to normal,
    // but this is terrible logic.
    private boolean shouldRenderFrame(long presentationTimeUs) {
        if (mRenderedSteps > 0 && mAvgStep < mTargetAvgStep) {
            // We are rendering too much. Drop this frame.
            // Always render first 2 frames, we need them to compute the avg.
            LOG.v("FRAME: Dropping. avg: " + mAvgStep + " target: " + mTargetAvgStep);
            long newLastStep = presentationTimeUs - mLastRenderedUs;
            float allSteps = (mAvgStep * mRenderedSteps) - mLastStep + newLastStep;
            mAvgStep = allSteps / mRenderedSteps; // we didn't add a step, just increased the last
            mLastStep = newLastStep;
            return false;
        } else {
            // Render this frame, since our average step is too long or exact.
            LOG.v("FRAME: RENDERING. avg: " + mAvgStep + " target: " + mTargetAvgStep + "New stepCount: " + (mRenderedSteps + 1));
            if (mRenderedSteps >= 0) {
                // Update the average value, since now we have mLastRenderedUs.
                long step = presentationTimeUs - mLastRenderedUs;
                float allSteps = (mAvgStep * mRenderedSteps) + step;
                mAvgStep = allSteps / (mRenderedSteps + 1); // we added a step, so +1
                mLastStep = step;
            }
            // Increment both
            mRenderedSteps++;
            mLastRenderedUs = presentationTimeUs;
            return true;
        }
        if (mFrameRateReciprocalSum > mOutFrameRateReciprocal) {
            mFrameRateReciprocalSum -= mOutFrameRateReciprocal;
            // render frame
            LOG.v("render this frame -> mFrameRateReciprocalSum: " + mFrameRateReciprocalSum);
            return true;
        }
        // drop frame
        LOG.v("drop this frame -> mFrameRateReciprocalSum: " + mFrameRateReciprocalSum);
        return false;
    }
}
