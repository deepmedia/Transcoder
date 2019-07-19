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

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.Build;

import com.otaliastudios.transcoder.TranscoderOptions;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.strategy.OutputStrategy;
import com.otaliastudios.transcoder.strategy.OutputStrategyException;
import com.otaliastudios.transcoder.transcode.AudioTrackTranscoder;
import com.otaliastudios.transcoder.transcode.NoOpTrackTranscoder;
import com.otaliastudios.transcoder.transcode.PassThroughTrackTranscoder;
import com.otaliastudios.transcoder.transcode.TrackTranscoder;
import com.otaliastudios.transcoder.transcode.VideoTrackTranscoder;
import com.otaliastudios.transcoder.engine.internal.ISO6709LocationParser;
import com.otaliastudios.transcoder.internal.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Internal engine, do not use this directly.
 */
public class TranscoderEngine {
    private static final String TAG = TranscoderEngine.class.getSimpleName();
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
    private Map<TrackType, TrackTranscoder> mTranscoders = new HashMap<>();
    private Tracks mTracks;
    private MediaExtractor mExtractor;
    private MediaMuxer mMuxer;
    private volatile double mProgress;
    private ProgressCallback mProgressCallback;
    private long mDurationUs;

    /**
     * Do not use this constructor unless you know what you are doing.
     */
    public TranscoderEngine() { }

    public void setDataSource(@NonNull DataSource dataSource) {
        mDataSource = dataSource;
    }

    public void setProgressCallback(@Nullable ProgressCallback progressCallback) {
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
     * @throws IOException when input or output file could not be opened.
     * @throws InvalidOutputFormatException when output format is not supported.
     * @throws InterruptedException when cancel to transcode
     * @throws ValidatorException if validator decides transcoding is not needed.
     */
    public void transcode(@NonNull TranscoderOptions options) throws IOException, InterruptedException {
        if (mDataSource == null) {
            throw new IllegalStateException("Data source is not set.");
        }
        try {
            // NOTE: use single extractor to keep from running out audio track fast.
            mExtractor = new MediaExtractor();
            mDataSource.apply(mExtractor);
            mMuxer = new MediaMuxer(options.getOutputPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            setUpMetadata();
            setupTrackTranscoders(options);
            runPipelines();
            mMuxer.stop();
        } finally {
            try {
                TrackTranscoder videoTranscoder = mTranscoders.get(TrackType.VIDEO);
                if (videoTranscoder != null) videoTranscoder.release();
                TrackTranscoder audioTranscoder = mTranscoders.get(TrackType.AUDIO);
                if (audioTranscoder != null) audioTranscoder.release();
                mTranscoders.clear();

                if (mExtractor != null) {
                    mExtractor.release();
                    mExtractor = null;
                }
            } catch (RuntimeException e) {
                // Too fatal to make alive the app, because it may leak native resources.
                //noinspection ThrowFromFinallyBlock
                throw new Error("Could not shutdown extractor, codecs and muxer pipeline.", e);
            }
            try {
                if (mMuxer != null) {
                    mMuxer.release();
                    mMuxer = null;
                }
            } catch (RuntimeException e) {
                LOG.e("Failed to release muxer.", e);
            }
        }
    }

    private void setUpMetadata() {
        MediaMetadataRetriever mediaMetadataRetriever = new MediaMetadataRetriever();
        mDataSource.apply(mediaMetadataRetriever);

        String rotationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        try {
            mMuxer.setOrientationHint(Integer.parseInt(rotationString));
        } catch (NumberFormatException e) {
            // skip
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            String locationString = mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
            if (locationString != null) {
                float[] location = new ISO6709LocationParser().parse(locationString);
                if (location != null) {
                    mMuxer.setLocation(location[0], location[1]);
                } else {
                    LOG.v("Failed to parse the location metadata: " + locationString);
                }
            }
        }

        try {
            mDurationUs = Long.parseLong(mediaMetadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
        } catch (NumberFormatException e) {
            mDurationUs = -1;
        }
        LOG.v("Duration (us): " + mDurationUs);
    }

    private void setUpTrackTranscoder(@NonNull TranscoderOptions options,
                                      @NonNull TranscoderMuxer muxer,
                                      @NonNull TrackType type) {
        TrackStatus status;
        TrackTranscoder transcoder;
        MediaFormat inputFormat = mTracks.format(type);
        MediaFormat outputFormat = null;
        if (!mTracks.has(type)) {
            transcoder = new NoOpTrackTranscoder();
            status = TrackStatus.ABSENT;
        } else {
            int index = mTracks.index(type);
            OutputStrategy strategy;
            switch (type) {
                case VIDEO: strategy = options.getVideoOutputStrategy(); break;
                case AUDIO: strategy = options.getAudioOutputStrategy(); break;
                default: throw new RuntimeException("Unknown type: " + type);
            }
            try {
                outputFormat = strategy.createOutputFormat(inputFormat);
                if (outputFormat == null) {
                    transcoder = new NoOpTrackTranscoder();
                    status = TrackStatus.REMOVING;
                } else if (outputFormat == inputFormat) {
                    transcoder = new PassThroughTrackTranscoder(mExtractor, index, muxer, type);
                    status = TrackStatus.PASS_THROUGH;
                } else {
                    switch (type) {
                        case VIDEO: transcoder = new VideoTrackTranscoder(mExtractor, index, muxer); break;
                        case AUDIO: transcoder = new AudioTrackTranscoder(mExtractor, index, muxer); break;
                        default: throw new RuntimeException("Unknown type: " + type);
                    }
                    status = TrackStatus.COMPRESSING;
                }
            } catch (OutputStrategyException strategyException) {
                if (strategyException.getType() == OutputStrategyException.TYPE_ALREADY_COMPRESSED) {
                    // Should not abort, because the other track might need compression. Use a pass through.
                    transcoder = new PassThroughTrackTranscoder(mExtractor, index, muxer, type);
                    status = TrackStatus.PASS_THROUGH;
                } else { // Abort.
                    throw strategyException;
                }
            }
        }
        mTracks.status(type, status);
        // Just to respect nullability in setUp().
        if (outputFormat == null) outputFormat = inputFormat;
        transcoder.setUp(outputFormat);
        mTranscoders.put(type, transcoder);
    }

    private void setupTrackTranscoders(@NonNull TranscoderOptions options) {
        mTracks = Tracks.create(mExtractor);
        TranscoderMuxer muxer = new TranscoderMuxer(mMuxer, mTracks);
        setUpTrackTranscoder(options, muxer, TrackType.VIDEO);
        setUpTrackTranscoder(options, muxer, TrackType.AUDIO);

        TrackStatus videoStatus = mTracks.status(TrackType.VIDEO);
        TrackStatus audioStatus = mTracks.status(TrackType.AUDIO);
        if (!options.getValidator().validate(videoStatus, audioStatus)) {
            throw new ValidatorException("Validator returned false.");
        }
        if (videoStatus.isTranscoding()) mExtractor.selectTrack(mTracks.index(TrackType.VIDEO));
        if (audioStatus.isTranscoding()) mExtractor.selectTrack(mTracks.index(TrackType.AUDIO));
    }

    @SuppressWarnings("ConstantConditions")
    private void runPipelines() throws InterruptedException {
        long loopCount = 0;
        if (mDurationUs <= 0) {
            double progress = PROGRESS_UNKNOWN;
            mProgress = progress;
            if (mProgressCallback != null) mProgressCallback.onProgress(progress); // unknown
        }
        TrackTranscoder videoTranscoder = mTranscoders.get(TrackType.VIDEO);
        TrackTranscoder audioTranscoder = mTranscoders.get(TrackType.AUDIO);
        while (!(videoTranscoder.isFinished() && audioTranscoder.isFinished())) {
            boolean stepped = videoTranscoder.stepPipeline() || audioTranscoder.stepPipeline();
            loopCount++;
            if (mDurationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                double videoProgress = getTranscoderProgress(videoTranscoder, mTracks.status(TrackType.VIDEO));
                double audioProgress = getTranscoderProgress(audioTranscoder, mTracks.status(TrackType.AUDIO));
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
        return Math.min(1.0, (double) transcoder.getLastWrittenPresentationTime() / mDurationUs);
    }

    private int getTranscodersCount() {
        int count = 0;
        if (mTracks.status(TrackType.AUDIO).isTranscoding()) count++;
        if (mTracks.status(TrackType.VIDEO).isTranscoding()) count++;
        return (count > 0) ? count : 1;
    }
}
