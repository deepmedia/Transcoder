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
package com.otaliastudios.transcoder.engine;

import android.media.MediaFormat;

import com.otaliastudios.transcoder.TranscoderOptions;
import com.otaliastudios.transcoder.internal.TrackTypeMap;
import com.otaliastudios.transcoder.internal.ValidatorException;
import com.otaliastudios.transcoder.sink.DataSink;
import com.otaliastudios.transcoder.sink.InvalidOutputFormatException;
import com.otaliastudios.transcoder.sink.MediaMuxerDataSink;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.strategy.TrackStrategy;
import com.otaliastudios.transcoder.strategy.TrackStrategyException;
import com.otaliastudios.transcoder.transcode.AudioTrackTranscoder;
import com.otaliastudios.transcoder.transcode.NoOpTrackTranscoder;
import com.otaliastudios.transcoder.transcode.PassThroughTrackTranscoder;
import com.otaliastudios.transcoder.transcode.TrackTranscoder;
import com.otaliastudios.transcoder.transcode.VideoTrackTranscoder;
import com.otaliastudios.transcoder.internal.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Internal engine, do not use this directly.
 */
public class Engine {

    private static final String TAG = Engine.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    private static final double PROGRESS_UNKNOWN = -1.0;
    private static final long SLEEP_TO_WAIT_TRACK_TRANSCODERS = 10;
    private static final long PROGRESS_INTERVAL_STEPS = 10;


    public interface ProgressCallback {

        /**
         * Called to notify progress. Same thread which initiated transcode is used.
         * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
         */
        void onProgress(double progress);
    }

    private DataSource mDataSource;
    private DataSink mDataSink;
    private TrackTypeMap<TrackTranscoder> mTranscoders = new TrackTypeMap<>();
    private TrackTypeMap<TrackStatus> mStatuses = new TrackTypeMap<>();
    private volatile double mProgress;
    private ProgressCallback mProgressCallback;
    private long mDurationUs;

    public Engine(@NonNull DataSource dataSource, @Nullable ProgressCallback progressCallback) {
        mDataSource = dataSource;
        mProgressCallback = progressCallback;
    }

    /**
     * NOTE: This method is thread safe.
     * @return the current progress
     */
    @SuppressWarnings("unused")
    public double getProgress() {
        return mProgress;
    }

    /**
     * Performs transcoding. Blocks current thread.
     *
     * @param options Transcoding options.
     * @throws InvalidOutputFormatException when output format is not supported.
     * @throws InterruptedException when cancel to transcode
     * @throws ValidatorException if validator decides transcoding is not needed.
     */
    public void transcode(@NonNull TranscoderOptions options) throws InterruptedException {
        try {
            // NOTE: use single extractor to keep from running out audio track fast.
            mDataSink = new MediaMuxerDataSink(options.getOutputPath());
            mDataSink.setOrientation((mDataSource.getOrientation() + options.getRotation()) % 360);
            double[] location = mDataSource.getLocation();
            if (location != null) mDataSink.setLocation(location[0], location[1]);
            mDurationUs = mDataSource.getDurationUs();
            LOG.v("Duration (us): " + mDurationUs);
            setUpTrackTranscoders(options);
            runPipelines();
            mDataSink.stop();
        } finally {
            try {
                mTranscoders.require(TrackType.VIDEO).release();
                mTranscoders.require(TrackType.AUDIO).release();
                mTranscoders.clear();
            } catch (RuntimeException e) {
                // Too fatal to make alive the app, because it may leak native resources.
                //noinspection ThrowFromFinallyBlock
                throw new Error("Could not shutdown extractor, codecs and muxer pipeline.", e);
            }
            mDataSink.release();
        }
    }

    private void setUpTrackTranscoder(@NonNull TranscoderOptions options,
                                      @NonNull TrackType type) {
        TrackStatus status;
        TrackTranscoder transcoder;
        MediaFormat inputFormat = mDataSource.getFormat(type);
        MediaFormat outputFormat = null;
        if (inputFormat == null) {
            transcoder = new NoOpTrackTranscoder();
            status = TrackStatus.ABSENT;
        } else {
            TrackStrategy strategy;
            switch (type) {
                case VIDEO: strategy = options.getVideoTrackStrategy(); break;
                case AUDIO: strategy = options.getAudioTrackStrategy(); break;
                default: throw new RuntimeException("Unknown type: " + type);
            }
            try {
                outputFormat = strategy.createOutputFormat(inputFormat);
                if (outputFormat == null) {
                    transcoder = new NoOpTrackTranscoder();
                    status = TrackStatus.REMOVING;
                } else if (outputFormat == inputFormat) {
                    transcoder = new PassThroughTrackTranscoder(mDataSource, mDataSink, type, options.getTimeInterpolator());
                    status = TrackStatus.PASS_THROUGH;
                } else {
                    switch (type) {
                        case VIDEO: transcoder = new VideoTrackTranscoder(mDataSource, mDataSink, options.getTimeInterpolator()); break;
                        case AUDIO: transcoder = new AudioTrackTranscoder(mDataSource, mDataSink, options.getTimeInterpolator(), options.getAudioStretcher()); break;
                        default: throw new RuntimeException("Unknown type: " + type);
                    }
                    status = TrackStatus.COMPRESSING;
                }
            } catch (TrackStrategyException strategyException) {
                if (strategyException.getType() == TrackStrategyException.TYPE_ALREADY_COMPRESSED) {
                    // Should not abort, because the other track might need compression. Use a pass through.
                    transcoder = new PassThroughTrackTranscoder(mDataSource, mDataSink, type, options.getTimeInterpolator());
                    status = TrackStatus.PASS_THROUGH;
                } else { // Abort.
                    throw strategyException;
                }
            }
        }
        mDataSource.setTrackStatus(type, status);
        mDataSink.setTrackStatus(type, status);
        mStatuses.set(type, status);
        // Just to respect nullability in setUp().
        if (outputFormat == null) outputFormat = new MediaFormat();
        transcoder.setUp(outputFormat);
        mTranscoders.set(type, transcoder);
    }

    private void setUpTrackTranscoders(@NonNull TranscoderOptions options) {
        setUpTrackTranscoder(options, TrackType.VIDEO);
        setUpTrackTranscoder(options, TrackType.AUDIO);

        TrackStatus videoStatus = mStatuses.require(TrackType.VIDEO);
        TrackStatus audioStatus = mStatuses.require(TrackType.AUDIO);
        //noinspection UnusedAssignment
        boolean ignoreValidatorResult = false;

        // If we have to apply some rotation, and the video should be transcoded,
        // ignore any Validator trying to abort the operation. The operation must happen
        // because we must apply the rotation.
        ignoreValidatorResult = videoStatus.isTranscoding() && options.getRotation() != 0;

        // Validate and go on.
        if (!options.getValidator().validate(videoStatus, audioStatus)
                && !ignoreValidatorResult) {
            throw new ValidatorException("Validator returned false.");
        }
    }

    private void runPipelines() throws InterruptedException {
        long loopCount = 0;
        if (mDurationUs <= 0) {
            double progress = PROGRESS_UNKNOWN;
            mProgress = progress;
            if (mProgressCallback != null) mProgressCallback.onProgress(progress); // unknown
        }
        TrackTranscoder videoTranscoder = mTranscoders.require(TrackType.VIDEO);
        TrackTranscoder audioTranscoder = mTranscoders.require(TrackType.AUDIO);
        while (!(videoTranscoder.isFinished() && audioTranscoder.isFinished())) {
            if (Thread.interrupted()) {
                throw new InterruptedException();
            }
            boolean stepped = videoTranscoder.transcode() || audioTranscoder.transcode();
            loopCount++;
            if (mDurationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                double videoProgress = getTranscoderProgress(videoTranscoder, mStatuses.require(TrackType.VIDEO));
                double audioProgress = getTranscoderProgress(audioTranscoder, mStatuses.require(TrackType.AUDIO));
                LOG.i("progress - video:" + videoProgress + " audio:" + audioProgress);
                double progress = (videoProgress + audioProgress) / getTranscodersCount();
                mProgress = progress;
                if (mProgressCallback != null) mProgressCallback.onProgress(progress);
            }
            if (!stepped) {
                Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS);
            }
        }
    }

    private double getTranscoderProgress(@NonNull TrackTranscoder transcoder, @NonNull TrackStatus status) {
        if (!status.isTranscoding()) return 0.0;
        if (transcoder.isFinished()) return 1.0;
        return Math.min(1.0, (double) transcoder.getLastPresentationTime() / mDurationUs);
    }

    private int getTranscodersCount() {
        int count = 0;
        if (mStatuses.require(TrackType.AUDIO).isTranscoding()) count++;
        if (mStatuses.require(TrackType.VIDEO).isTranscoding()) count++;
        return (count > 0) ? count : 1;
    }
}
