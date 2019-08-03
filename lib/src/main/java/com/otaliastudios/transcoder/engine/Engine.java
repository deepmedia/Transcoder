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
import com.otaliastudios.transcoder.transcode.AudioTrackTranscoder;
import com.otaliastudios.transcoder.transcode.NoOpTrackTranscoder;
import com.otaliastudios.transcoder.transcode.PassThroughTrackTranscoder;
import com.otaliastudios.transcoder.transcode.TrackTranscoder;
import com.otaliastudios.transcoder.transcode.VideoTrackTranscoder;
import com.otaliastudios.transcoder.internal.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    private DataSink mDataSink;
    private TrackTypeMap<List<DataSource>> mDataSources = new TrackTypeMap<>();
    private TrackTypeMap<TrackTranscoder> mTranscoders = new TrackTypeMap<>();
    private TrackTypeMap<TrackStatus> mStatuses = new TrackTypeMap<>();
    private volatile double mProgress;
    private ProgressCallback mProgressCallback;
    private long mTotalDurationUs;

    public Engine(@Nullable ProgressCallback progressCallback) {
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

    private void setProgress(double progress) {
        mProgress = progress;
        if (mProgressCallback != null) {
            mProgressCallback.onProgress(progress);
        }
    }

    private boolean hasVideoSources() {
        return !mDataSources.requireVideo().isEmpty();
    }

    private boolean hasAudioSources() {
        return !mDataSources.requireAudio().isEmpty();
    }

    private Set<DataSource> getUniqueSources() {
        Set<DataSource> sources = new HashSet<>();
        sources.addAll(mDataSources.requireVideo());
        sources.addAll(mDataSources.requireAudio());
        return sources;
    }

    /**
     * Performs transcoding. Blocks current thread.
     *
     * @param options Transcoding options.
     * @throws InvalidOutputFormatException when output format is not supported.
     * @throws InterruptedException when cancel to transcode
     */
    public void transcode(@NonNull TranscoderOptions options) throws InterruptedException {
        mDataSink = new MediaMuxerDataSink(options.getOutputPath());
        mDataSources.setVideo(options.getVideoDataSources());
        mDataSources.setAudio(options.getAudioDataSources());

        // Pass metadata from DataSource to DataSink
        if (hasVideoSources()) {
            DataSource firstVideoSource = mDataSources.requireVideo().get(0);
            mDataSink.setOrientation((firstVideoSource.getOrientation() + options.getRotation()) % 360);
        }
        for (DataSource locationSource : getUniqueSources()) {
            double[] location = locationSource.getLocation();
            if (location != null) {
                mDataSink.setLocation(location[0], location[1]);
                break;
            }
        }

        // Compute total duration: it is the minimum between the two.
        long audioDurationUs = hasAudioSources() ? 0 : Long.MAX_VALUE;
        long videoDurationUs = hasVideoSources() ? 0 : Long.MAX_VALUE;
        for (DataSource source : options.getVideoDataSources()) videoDurationUs += source.getDurationUs();
        for (DataSource source : options.getAudioDataSources()) audioDurationUs += source.getDurationUs();
        mTotalDurationUs = Math.min(audioDurationUs, videoDurationUs);
        LOG.v("Duration (us): " + mTotalDurationUs);

        // Set up transcoders.
        int tracks = 0;
        setUpTrackTranscoders(TrackType.VIDEO, options.getVideoTrackStrategy(), options);
        setUpTrackTranscoders(TrackType.AUDIO, options.getAudioTrackStrategy(), options);
        TrackStatus videoStatus = mStatuses.require(TrackType.VIDEO);
        TrackStatus audioStatus = mStatuses.require(TrackType.AUDIO);
        TrackTranscoder videoTranscoder = mTranscoders.require(TrackType.VIDEO);
        TrackTranscoder audioTranscoder = mTranscoders.require(TrackType.AUDIO);
        if (videoStatus.isTranscoding()) tracks++;
        if (audioStatus.isTranscoding()) tracks++;
        tracks = Math.max(1, tracks);

        // Pass to Validator.
        //noinspection UnusedAssignment
        boolean ignoreValidatorResult = false;
        // If we have to apply some rotation, and the video should be transcoded,
        // ignore any Validator trying to abort the operation. The operation must happen
        // because we must apply the rotation.
        ignoreValidatorResult = videoStatus.isTranscoding() && options.getRotation() != 0;
        if (!options.getValidator().validate(videoStatus, audioStatus) && !ignoreValidatorResult) {
            throw new ValidatorException("Validator returned false.");
        }

        // Do the actual transcoding work.
        long loopCount = 0;
        if (mTotalDurationUs <= 0) {
            setProgress(PROGRESS_UNKNOWN);
        }
        try {
            while (!(videoTranscoder.isFinished() && audioTranscoder.isFinished())) {
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
                boolean stepped = videoTranscoder.transcode() || audioTranscoder.transcode();
                if (mTotalDurationUs > 0 && ++loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                    double videoProgress = getTranscoderProgress(videoTranscoder, videoStatus);
                    double audioProgress = getTranscoderProgress(audioTranscoder, audioStatus);
                    LOG.i("progress - video:" + videoProgress + " audio:" + audioProgress);
                    setProgress((videoProgress + audioProgress) / tracks);
                }
                if (!stepped) {
                    Thread.sleep(SLEEP_TO_WAIT_TRACK_TRANSCODERS);
                }
            }
            mDataSink.stop();
        } finally {
            try {
                videoTranscoder.release();
                audioTranscoder.release();
            } catch (RuntimeException e) {
                // Too fatal to make alive the app, because it may leak native resources.
                //noinspection ThrowFromFinallyBlock
                throw new Error("Could not shutdown extractor, codecs and muxer pipeline.", e);
            }
            mDataSink.release();
        }
    }

    private void setUpTrackTranscoder(@NonNull TrackType type,
                                      @NonNull TrackStrategy strategy,
                                      @NonNull TranscoderOptions options) {
        TrackStatus status = TrackStatus.ABSENT;
        TrackTranscoder transcoder = new NoOpTrackTranscoder();
        final MediaFormat inputFormat = mDataSource.getTrackFormat(type);
        MediaFormat outputFormat = new MediaFormat();
        if (inputFormat != null) {
            //noinspection ArraysAsListWithZeroOrOneArgument
            status = strategy.createOutputFormat(Arrays.asList(inputFormat), outputFormat);
            switch (status) {
                case ABSENT: throw new IllegalArgumentException("Strategies should not return ABSENT.");
                case REMOVING: break; // We'll use NoOpTrackTranscoder.
                case PASS_THROUGH: {
                    transcoder = new PassThroughTrackTranscoder(mDataSource,
                            mDataSink, type, options.getTimeInterpolator());
                    break;
                }
                case COMPRESSING: {
                    transcoder = createTrackTranscoder(type, options);
                    break;
                }
            }
        }
        if (status.isTranscoding()) mDataSource.selectTrack(type);
        mDataSink.setTrackStatus(type, status);
        mStatuses.set(type, status);
        transcoder.setUp(outputFormat);
        mTranscoders.set(type, transcoder);
    }

    @NonNull
    private TrackTranscoder createTrackTranscoder(@NonNull TrackType type,
                                                  @NonNull TranscoderOptions options) {
        switch (type) {
            case VIDEO: return new VideoTrackTranscoder(mDataSource, mDataSink,
                    options.getTimeInterpolator());
            case AUDIO: return new AudioTrackTranscoder(mDataSource, mDataSink,
                    options.getTimeInterpolator(), options.getAudioStretcher());
            default: throw new RuntimeException("Unknown type: " + type);
        }
    }

    private double getTranscoderProgress(@NonNull TrackTranscoder transcoder, @NonNull TrackStatus status) {
        if (!status.isTranscoding()) return 0.0;
        if (transcoder.isFinished()) return 1.0;
        return Math.min(1.0, (double) transcoder.getLastPresentationTime() / mTotalDurationUs);
    }
}
