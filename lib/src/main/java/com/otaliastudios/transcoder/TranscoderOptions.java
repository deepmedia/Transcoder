package com.otaliastudios.transcoder;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.source.FileDescriptorDataSource;
import com.otaliastudios.transcoder.source.FilePathDataSource;
import com.otaliastudios.transcoder.source.UriDataSource;
import com.otaliastudios.transcoder.strategy.DefaultAudioStrategy;
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategies;
import com.otaliastudios.transcoder.strategy.OutputStrategy;
import com.otaliastudios.transcoder.time.DefaultTimeInterpolator;
import com.otaliastudios.transcoder.time.TimeInterpolator;
import com.otaliastudios.transcoder.validator.DefaultValidator;
import com.otaliastudios.transcoder.validator.Validator;

import java.io.FileDescriptor;
import java.util.concurrent.Future;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Collects transcoding options consumed by {@link Transcoder}.
 */
public class TranscoderOptions {

    private TranscoderOptions() {}

    private String outPath;
    private DataSource dataSource;
    private OutputStrategy audioOutputStrategy;
    private OutputStrategy videoOutputStrategy;
    private Validator validator;
    private int rotation;
    private TimeInterpolator timeInterpolator;

    TranscoderListener listener;
    Handler listenerHandler;

    @NonNull
    public String getOutputPath() {
        return outPath;
    }

    @NonNull
    @SuppressWarnings("WeakerAccess")
    public DataSource getDataSource() {
        return dataSource;
    }

    @NonNull
    public OutputStrategy getAudioOutputStrategy() {
        return audioOutputStrategy;
    }

    @NonNull
    public OutputStrategy getVideoOutputStrategy() {
        return videoOutputStrategy;
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

    public static class Builder {
        private String outPath;
        private DataSource dataSource;
        private TranscoderListener listener;
        private Handler listenerHandler;
        private OutputStrategy audioOutputStrategy;
        private OutputStrategy videoOutputStrategy;
        private Validator validator;
        private int rotation;
        private TimeInterpolator timeInterpolator;

        Builder(@NonNull String outPath) {
            this.outPath = outPath;
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder setDataSource(@NonNull DataSource dataSource) {
            this.dataSource = dataSource;
            return this;
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder setDataSource(@NonNull FileDescriptor fileDescriptor) {
            this.dataSource = new FileDescriptorDataSource(fileDescriptor);
            return this;
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder setDataSource(@NonNull String inPath) {
            this.dataSource = new FilePathDataSource(inPath);
            return this;
        }

        @NonNull
        @SuppressWarnings("unused")
        public Builder setDataSource(@NonNull Context context, @NonNull Uri uri) {
            this.dataSource = new UriDataSource(context, uri);
            return this;
        }

        /**
         * Sets the audio output strategy. If absent, this defaults to
         * {@link com.otaliastudios.transcoder.strategy.DefaultAudioStrategy}.
         *
         * @param outputStrategy the desired strategy
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("unused")
        public Builder setAudioOutputStrategy(@Nullable OutputStrategy outputStrategy) {
            this.audioOutputStrategy = outputStrategy;
            return this;
        }

        /**
         * Sets the video output strategy. If absent, this defaults to the 16:9
         * strategy returned by {@link DefaultVideoStrategies#for720x1280()}.
         *
         * @param outputStrategy the desired strategy
         * @return this for chaining
         */
        @NonNull
        @SuppressWarnings("unused")
        public Builder setVideoOutputStrategy(@Nullable OutputStrategy outputStrategy) {
            this.videoOutputStrategy = outputStrategy;
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
        public Builder setTimeInterpolator(@NonNull TimeInterpolator timeInterpolator) {
            this.timeInterpolator = timeInterpolator;
            return this;
        }

        @NonNull
        public TranscoderOptions build() {
            if (listener == null) {
                throw new IllegalStateException("listener can't be null");
            }
            if (dataSource == null) {
                throw new IllegalStateException("data source can't be null");
            }
            if (outPath == null) {
                throw new IllegalStateException("out path can't be null");
            }
            if (rotation != 0 && rotation != 90 && rotation != 180 && rotation != 270) {
                throw new IllegalArgumentException("Accepted values for rotation are 0, 90, 180, 270");
            }
            if (listenerHandler == null) {
                Looper looper = Looper.myLooper();
                if (looper == null) looper = Looper.getMainLooper();
                listenerHandler = new Handler(looper);
            }
            if (audioOutputStrategy == null) {
                audioOutputStrategy = new DefaultAudioStrategy(DefaultAudioStrategy.AUDIO_CHANNELS_AS_IS);
            }
            if (videoOutputStrategy == null) {
                videoOutputStrategy = DefaultVideoStrategies.for720x1280();
            }
            if (validator == null) {
                validator = new DefaultValidator();
            }
            if (timeInterpolator == null) {
                timeInterpolator = new DefaultTimeInterpolator();
            }
            TranscoderOptions options = new TranscoderOptions();
            options.listener = listener;
            options.dataSource = dataSource;
            options.outPath = outPath;
            options.listenerHandler = listenerHandler;
            options.audioOutputStrategy = audioOutputStrategy;
            options.videoOutputStrategy = videoOutputStrategy;
            options.validator = validator;
            options.rotation = rotation;
            options.timeInterpolator = timeInterpolator;
            return options;
        }

        @NonNull
        public Future<Void> transcode() {
            return Transcoder.getInstance().transcode(build());
        }
    }
}
