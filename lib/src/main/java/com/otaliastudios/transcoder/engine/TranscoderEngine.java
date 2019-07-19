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
import com.otaliastudios.transcoder.strategy.OutputStrategyException;
import com.otaliastudios.transcoder.transcode.AudioTrackTranscoder;
import com.otaliastudios.transcoder.transcode.NoOpTrackTranscoder;
import com.otaliastudios.transcoder.transcode.PassThroughTrackTranscoder;
import com.otaliastudios.transcoder.transcode.TrackTranscoder;
import com.otaliastudios.transcoder.transcode.VideoTrackTranscoder;
import com.otaliastudios.transcoder.engine.internal.ISO6709LocationParser;
import com.otaliastudios.transcoder.internal.Logger;

import java.io.IOException;

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
    private DataSource mDataSource;
    private TrackTranscoder mVideoTrackTranscoder;
    private TrackTranscoder mAudioTrackTranscoder;
    private TracksInfo mTracksInfo;
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
            setupMetadata();
            setupTrackTranscoders(options);
            runPipelines();
            mMuxer.stop();
        } finally {
            try {
                if (mVideoTrackTranscoder != null) {
                    mVideoTrackTranscoder.release();
                    mVideoTrackTranscoder = null;
                }
                if (mAudioTrackTranscoder != null) {
                    mAudioTrackTranscoder.release();
                    mAudioTrackTranscoder = null;
                }
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

    private void setupMetadata() {
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

    private void setupTrackTranscoders(@NonNull TranscoderOptions options) {
        mTracksInfo = TracksInfo.fromExtractor(mExtractor);
        QueuedMuxer queuedMuxer = new QueuedMuxer(mMuxer, mTracksInfo, new QueuedMuxer.Listener() {
            @Override
            public void onDetermineOutputFormat() {
                MediaFormatValidator formatValidator = new MediaFormatValidator();
                formatValidator.validateVideoOutputFormat(mVideoTrackTranscoder.getDeterminedFormat());
                formatValidator.validateAudioOutputFormat(mAudioTrackTranscoder.getDeterminedFormat());
            }
        });
        TrackStatus videoStatus, audioStatus;

        // Video format.
        if (!mTracksInfo.hasVideo()) {
            mVideoTrackTranscoder = new NoOpTrackTranscoder();
            videoStatus = TrackStatus.ABSENT;
        } else {
            try {
                MediaFormat videoFormat = options.getVideoOutputStrategy().createOutputFormat(mTracksInfo.videoTrackFormat);
                if (videoFormat == null) {
                    mVideoTrackTranscoder = new NoOpTrackTranscoder();
                    videoStatus = TrackStatus.REMOVING;
                } else if (videoFormat == mTracksInfo.videoTrackFormat) {
                    mVideoTrackTranscoder = new PassThroughTrackTranscoder(mExtractor,
                            mTracksInfo.videoTrackIndex, queuedMuxer, QueuedMuxer.SampleType.VIDEO);
                    videoStatus = TrackStatus.PASS_THROUGH;
                } else {
                    float inWidth = mTracksInfo.videoTrackFormat.getInteger(MediaFormat.KEY_WIDTH);
                    float inHeight = mTracksInfo.videoTrackFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    mVideoTrackTranscoder = new VideoTrackTranscoder(mExtractor,
                            mTracksInfo.videoTrackIndex, videoFormat, queuedMuxer, inWidth / inHeight);
                    videoStatus = TrackStatus.COMPRESSING;
                }
            } catch (OutputStrategyException strategyException) {
                if (strategyException.getType() == OutputStrategyException.TYPE_ALREADY_COMPRESSED) {
                    // Should not abort, because the other track might need compression. Use a pass through.
                    mVideoTrackTranscoder = new PassThroughTrackTranscoder(mExtractor,
                            mTracksInfo.videoTrackIndex, queuedMuxer, QueuedMuxer.SampleType.VIDEO);
                    videoStatus = TrackStatus.PASS_THROUGH;
                } else { // Abort.
                    throw strategyException;
                }
            }
        }
        mTracksInfo.videoTrackStatus = videoStatus;
        mVideoTrackTranscoder.setup();

        // Audio format.
        if (!mTracksInfo.hasAudio()) {
            mAudioTrackTranscoder = new NoOpTrackTranscoder();
            audioStatus = TrackStatus.ABSENT;
        } else {
            try {
                MediaFormat audioFormat = options.getAudioOutputStrategy().createOutputFormat(mTracksInfo.audioTrackFormat);
                if (audioFormat == null) {
                    mAudioTrackTranscoder = new NoOpTrackTranscoder();
                    audioStatus = TrackStatus.REMOVING;
                } else if (audioFormat == mTracksInfo.audioTrackFormat) {
                    mAudioTrackTranscoder = new PassThroughTrackTranscoder(mExtractor,
                            mTracksInfo.audioTrackIndex, queuedMuxer, QueuedMuxer.SampleType.AUDIO);
                    audioStatus = TrackStatus.PASS_THROUGH;
                } else {
                    mAudioTrackTranscoder = new AudioTrackTranscoder(mExtractor,
                            mTracksInfo.audioTrackIndex, audioFormat, queuedMuxer);
                    audioStatus = TrackStatus.COMPRESSING;
                }
            } catch (OutputStrategyException strategyException) {
                if (strategyException.getType() == OutputStrategyException.TYPE_ALREADY_COMPRESSED) {
                    // Should not abort, because the other track might need compression. Use a pass through.
                    mAudioTrackTranscoder = new PassThroughTrackTranscoder(mExtractor,
                            mTracksInfo.audioTrackIndex, queuedMuxer, QueuedMuxer.SampleType.AUDIO);
                    audioStatus = TrackStatus.PASS_THROUGH;
                } else { // Abort.
                    throw strategyException;
                }
            }
        }
        mTracksInfo.audioTrackStatus = audioStatus;
        mAudioTrackTranscoder.setup();

        if (!options.getValidator().validate(videoStatus, audioStatus)) {
            throw new ValidatorException("Validator returned false.");
        }

        if (videoStatus.isTranscoding()) mExtractor.selectTrack(mTracksInfo.videoTrackIndex);
        if (audioStatus.isTranscoding()) mExtractor.selectTrack(mTracksInfo.audioTrackIndex);
    }

    private void runPipelines() throws InterruptedException {
        long loopCount = 0;
        if (mDurationUs <= 0) {
            double progress = PROGRESS_UNKNOWN;
            mProgress = progress;
            if (mProgressCallback != null) mProgressCallback.onProgress(progress); // unknown
        }
        while (!(mVideoTrackTranscoder.isFinished() && mAudioTrackTranscoder.isFinished())) {
            boolean stepped = mVideoTrackTranscoder.stepPipeline() || mAudioTrackTranscoder.stepPipeline();
            loopCount++;
            if (mDurationUs > 0 && loopCount % PROGRESS_INTERVAL_STEPS == 0) {
                double videoProgress = getTranscoderProgress(mVideoTrackTranscoder, mTracksInfo.videoTrackStatus);
                double audioProgress = getTranscoderProgress(mAudioTrackTranscoder, mTracksInfo.audioTrackStatus);
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
        return Math.min(1.0, (double) transcoder.getWrittenPresentationTimeUs() / mDurationUs);
    }

    private int getTranscodersCount() {
        int count = 0;
        if (mTracksInfo.audioTrackStatus.isTranscoding()) count++;
        if (mTracksInfo.videoTrackStatus.isTranscoding()) count++;
        return (count > 0) ? count : 1;
    }

    public interface ProgressCallback {
        /**
         * Called to notify progress. Same thread which initiated transcode is used.
         *
         * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
         */
        void onProgress(double progress);
    }
}
