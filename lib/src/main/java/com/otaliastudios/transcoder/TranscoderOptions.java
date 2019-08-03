package com.otaliastudios.transcoder;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.source.FileDescriptorDataSource;
import com.otaliastudios.transcoder.source.FilePathDataSource;
import com.otaliastudios.transcoder.source.UriDataSource;
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy;
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategies;
import com.otaliastudios.transcoder.strategy.TrackStrategy;
import com.otaliastudios.transcoder.stretch.AudioStretcher;
import com.otaliastudios.transcoder.stretch.DefaultAudioStretcher;
import com.otaliastudios.transcoder.time.DefaultTimeInterpolator;
import com.otaliastudios.transcoder.time.SpeedTimeInterpolator;
import com.otaliastudios.transcoder.time.TimeInterpolator;
import com.otaliastudios.transcoder.validator.DefaultValidator;
import com.otaliastudios.transcoder.validator.Validator;

import java.io.FileDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Collects transcoding options consumed by {@link Transcoder}.
 */
public class TranscoderOptions {

    private TranscoderOptions() {}

    private String outPath;
    private List<DataSource> videoDataSources;
    private List<DataSource> audioDataSources;
    private TrackStrategy audioTrackStrategy;
    private TrackStrategy videoTrackStrategy;
    private Validator validator;
    private int rotation;
    private TimeInterpolator timeInterpolator;
    private AudioStretcher audioStretcher;

    TranscoderListener listener;
    Handler listenerHandler;

    @NonNull
    public String getOutputPath() {
        return outPath;
    }

    @NonNull
    @SuppressWarnings("WeakerAccess")
    public List<DataSource> getAudioDataSources() {
        return audioDataSources;
    }

    @NonNull
    @SuppressWarnings("WeakerAccess")
    public List<DataSource> getVideoDataSources() {
        return videoDataSources;
    }

    @NonNull
    public TrackStrategy getAudioTrackStrategy() {
        return audioTrackStrategy;
    }

    @NonNull
    public TrackStrategy getVideoTrackStrategy() {
        return videoTrackStrategy;
    }

    @NonNull
    public Validator getValidator() {
        return validator;
    }

    public int getRotation() {
        return rotation;
    }

    @NonNull
    public TimeInterpolator getTimeInterpolator() {
        return timeInterpolator;
    }

    @NonNull
    public AudioStretcher getAudioStretcher() {
        return audioStretcher;
    }

    public static class Builder {
        private String outPath;
        private final List<DataSource> audioDataSources = new ArrayList<>();
        private final List<DataSource> videoDataSources = new ArrayList<>();
        private TranscoderListener listener;
        private Handler listenerHandler;
        private TrackStrategy audioTrackStrategy;
        private TrackStrategy videoTrackStrategy;
        private Validator validator;
        private int rotation;
        private TimeInterpolator timeInterpolator;
        private AudioStretcher audioStretcher;

        Builder(@NonNull String outPath) {
            this.outPath = outPath;
        }

        @NonNull
        @SuppressWarnings("WeakerAccess")
        public Builder addDataSource(@NonNull DataSource dataSource) {
            audioDataSources.add(dataSource);
            videoDataSources.add(dataSource);
            return this;
        }

        @NonNull
        @SuppressWarnings("WeakerAccess")
        public Builder addDataSource(@NonNull TrackType type, @NonNull DataSource dataSource) {
            if (type == TrackType.AUDIO) {
                audioDataSources.add(dataSource);
            } else if (type == TrackType.VIDEO) {
                videoDataSources.add(dataSource);
            }
            return this;
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder addDataSource(@NonNull FileDescriptor fileDescriptor) {
            return addDataSource(new FileDescriptorDataSource(fileDescriptor));
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder addDataSource(@NonNull TrackType type, @NonNull FileDescriptor fileDescriptor) {
            return addDataSource(type, new FileDescriptorDataSource(fileDescriptor));
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder addDataSource(@NonNull String inPath) {
            return addDataSource(new FilePathDataSource(inPath));
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder addDataSource(@NonNull TrackType type, @NonNull String inPath) {
            return addDataSource(type, new FilePathDataSource(inPath));
        }

        @NonNull
        @SuppressWarnings({"unused", "UnusedReturnValue"})
        public Builder addDataSource(@NonNull Context context, @NonNull Uri uri) {
            return addDataSource(new UriDataSource(context, uri));
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder addDataSource(@NonNull TrackType type, @NonNull Context context, @NonNull Uri uri) {
            return addDataSource(type, new UriDataSource(context, uri));
        }

        /**
         * Sets the audio output strategy. If absent, this defaults to
         * {@link com.otaliastudios.transcoder.strategy.DefaultAudioStrategy}.
         *
         * @param trackStrategy the desired strategy
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("unused")
        public Builder setAudioTrackStrategy(@Nullable TrackStrategy trackStrategy) {
            this.audioTrackStrategy = trackStrategy;
            return this;
        }

        /**
         * Sets the video output strategy. If absent, this defaults to the 16:9
         * strategy returned by {@link DefaultVideoStrategies#for720x1280()}.
         *
         * @param trackStrategy the desired strategy
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("unused")
        public Builder setVideoTrackStrategy(@Nullable TrackStrategy trackStrategy) {
            this.videoTrackStrategy = trackStrategy;
            return this;
        }

        @NonNull
        public Builder setListener(@NonNull TranscoderListener listener) {
            this.listener = listener;
            return this;
        }

        /**
         * Sets an handler for {@link TranscoderListener} callbacks.
         * If null, this will default to the thread that starts the transcoding, if it
         * has a looper, or the UI thread otherwise.
         *
         * @param listenerHandler the thread to receive callbacks
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("WeakerAccess")
        public Builder setListenerHandler(@Nullable Handler listenerHandler) {
            this.listenerHandler = listenerHandler;
            return this;
        }

        /**
         * Sets a validator to understand whether the transcoding process should
         * stop before being started, based on the tracks status. Will default to
         * {@link com.otaliastudios.transcoder.validator.DefaultValidator}.
         *
         * @param validator the validator
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("unused")
        public Builder setValidator(@Nullable Validator validator) {
            this.validator = validator;
            return this;
        }

        /**
         * The clockwise rotation to be applied to the input video frames.
         * Defaults to 0, which leaves the input rotation unchanged.
         *
         * @param rotation either 0, 90, 180 or 270
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("unused")
        public Builder setRotation(int rotation) {
            this.rotation = rotation;
            return this;
        }

        /**
         * Sets a {@link TimeInterpolator} to change the frames timestamps - either video or
         * audio or both - before they are written into the output file.
         * Defaults to {@link com.otaliastudios.transcoder.time.DefaultTimeInterpolator}.
         *
         * @param timeInterpolator a time interpolator
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("WeakerAccess")
        public Builder setTimeInterpolator(@NonNull TimeInterpolator timeInterpolator) {
            this.timeInterpolator = timeInterpolator;
            return this;
        }

        /**
         * Shorthand for calling {@link #setTimeInterpolator(TimeInterpolator)}
         * and passing a {@link com.otaliastudios.transcoder.time.SpeedTimeInterpolator}.
         * This interpolator can modify the video speed by the given factor.
         *
         * @param speedFactor a factor, greather than 0
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("unused")
        public Builder setSpeed(float speedFactor) {
            return setTimeInterpolator(new SpeedTimeInterpolator(speedFactor));
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder setAudioStretcher(@NonNull AudioStretcher audioStretcher) {
            this.audioStretcher = audioStretcher;
            return this;
        }

        @NonNull
        public TranscoderOptions build() {
            if (listener == null) {
                throw new IllegalStateException("listener can't be null");
            }
            if (audioDataSources.isEmpty() && videoDataSources.isEmpty()) {
                throw new IllegalStateException("we need at least one data source");
            }
            if (outPath == null) {
                throw new IllegalStateException("out path can't be null");
            }
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                throw new IllegalArgumentException("Accepted values for rotation are 0, 90, 180, 270");
            }
            if (rotation != 0 && videoDataSources.size() > 1) {
                // TODO support this, by not using metadata and instead rotating the GL texture
                throw new IllegalStateException("Rotation should be 0 if you have more than one video source.");
            }
            if (listenerHandler == null) {
                Looper looper = Looper.myLooper();
                if (looper == null) looper = Looper.getMainLooper();
                listenerHandler = new Handler(looper);
            }
            if (audioTrackStrategy == null) {
                audioTrackStrategy = new DefaultAudioStrategy(DefaultAudioStrategy.AUDIO_CHANNELS_AS_IS);
            }
            if (videoTrackStrategy == null) {
                videoTrackStrategy = DefaultVideoStrategies.for720x1280();
            }
            if (validator == null) {
                validator = new DefaultValidator();
            }
            if (timeInterpolator == null) {
                timeInterpolator = new DefaultTimeInterpolator();
            }
            if (audioStretcher == null) {
                audioStretcher = new DefaultAudioStretcher();
            }
            TranscoderOptions options = new TranscoderOptions();
            options.listener = listener;
            options.audioDataSources = audioDataSources;
            options.videoDataSources = videoDataSources;
            options.outPath = outPath;
            options.listenerHandler = listenerHandler;
            options.audioTrackStrategy = audioTrackStrategy;
            options.videoTrackStrategy = videoTrackStrategy;
            options.validator = validator;
            options.rotation = rotation;
            options.timeInterpolator = timeInterpolator;
            options.audioStretcher = audioStretcher;
            return options;
        }

        @NonNull
        public Future<Void> transcode() {
            return Transcoder.getInstance().transcode(build());
        }
    }
}
