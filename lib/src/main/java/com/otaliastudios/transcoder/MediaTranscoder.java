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
package com.otaliastudios.transcoder;

import android.os.Handler;

import com.otaliastudios.transcoder.engine.MediaTranscoderEngine;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.validator.Validator;
import com.otaliastudios.transcoder.engine.ValidatorException;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;

public class MediaTranscoder {
    private static final String TAG = "MediaTranscoder";
    private static final Logger LOG = new Logger(TAG);

    /**
     * Constant for {@link Listener#onTranscodeCompleted(int)}.
     * Transcoding was executed successfully.
     */
    public static final int SUCCESS_TRANSCODED = 0;

    /**
     * Constant for {@link Listener#onTranscodeCompleted(int)}:
     * transcoding was not executed because it was considered
     * not necessary by the {@link Validator}.
     */
    public static final int SUCCESS_NOT_NEEDED = 1;

    private static volatile MediaTranscoder sMediaTranscoder;

    private class Factory implements ThreadFactory {
        private AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            return new Thread(runnable, TAG + " Thread #" + count.getAndIncrement());
        }
    }

    private ThreadPoolExecutor mExecutor;

    private MediaTranscoder() {
        // This executor will execute at most 'pool' tasks concurrently,
        // then queue all the others. CPU + 1 is used by AsyncTask.
        int pool = Runtime.getRuntime().availableProcessors() + 1;
        mExecutor = new ThreadPoolExecutor(pool, pool,
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<Runnable>(),
                new Factory());
    }

    @SuppressWarnings("WeakerAccess")
    @NonNull
    public static MediaTranscoder getInstance() {
        if (sMediaTranscoder == null) {
            synchronized (MediaTranscoder.class) {
                if (sMediaTranscoder == null) {
                    sMediaTranscoder = new MediaTranscoder();
                }
            }
        }
        return sMediaTranscoder;
    }

    /**
     * Starts building transcoder options.
     * Requires a non null absolute path to the output file.
     *
     * @param outPath path to output file
     * @return an options builder
     */
    @NonNull
    public static MediaTranscoderOptions.Builder into(@NonNull String outPath) {
        return new MediaTranscoderOptions.Builder(outPath);
    }

    /**
     * Transcodes video file asynchronously.
     *
     * @param options The transcoder options.
     * @return a Future that completes when transcoding is completed
     */
    @NonNull
    public Future<Void> transcode(@NonNull final MediaTranscoderOptions options) {
        final Listener listenerWrapper = new ListenerWrapper(options.listenerHandler,
                options.listener, options.getDataSource());
        return mExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    MediaTranscoderEngine engine = new MediaTranscoderEngine();
                    engine.setProgressCallback(new MediaTranscoderEngine.ProgressCallback() {
                        @Override
                        public void onProgress(final double progress) {
                            listenerWrapper.onTranscodeProgress(progress);
                        }
                    });
                    engine.setDataSource(options.getDataSource());
                    engine.transcode(options);
                    listenerWrapper.onTranscodeCompleted(SUCCESS_TRANSCODED);
                } catch (ValidatorException e) {
                    LOG.i("Validator has decided that the input is fine and transcoding is not necessary.");
                    listenerWrapper.onTranscodeCompleted(SUCCESS_NOT_NEEDED);
                } catch (InterruptedException e) {
                    LOG.i("Cancel transcode video file.", e);
                    listenerWrapper.onTranscodeCanceled();
                } catch (IOException e) {
                    LOG.w("Transcode failed: input source (" + options.getDataSource().toString() + ") not found"
                            + " or could not open output file ('" + options.getOutputPath() + "') .", e);
                    listenerWrapper.onTranscodeFailed(e);
                    throw e;
                } catch (RuntimeException e) {
                    LOG.e("Fatal error while transcoding, this might be invalid format or bug in engine or Android.", e);
                    listenerWrapper.onTranscodeFailed(e);
                    throw e;
                } catch (Throwable e) {
                    LOG.e("Unexpected error while transcoding", e);
                    listenerWrapper.onTranscodeFailed(e);
                    throw e;
                }
                return null;
            }
        });
    }

    /**
     * Listeners for transcoder events. All the callbacks are called on the handler
     * specified with {@link MediaTranscoderOptions.Builder#setListenerHandler(Handler)}.
     */
    public interface Listener {
        /**
         * Called to notify progress.
         *
         * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
         */
        void onTranscodeProgress(double progress);

        /**
         * Called when transcode completed. The success code can be either
         * {@link #SUCCESS_TRANSCODED} or {@link #SUCCESS_NOT_NEEDED}.
         *
         * @param successCode the success code
         */
        void onTranscodeCompleted(int successCode);

        /**
         * Called when transcode canceled.
         */
        void onTranscodeCanceled();

        /**
         * Called when transcode failed.
         * @param exception the failure exception
         */
        void onTranscodeFailed(@NonNull Throwable exception);
    }

    /**
     * Wraps a Listener and a DataSource object, ensuring that the source
     * is released when transcoding ends, fails or is canceled.
     *
     * It posts events on the given handler.
     */
    private static class ListenerWrapper implements Listener {

        private Handler mHandler;
        private Listener mListener;
        private DataSource mDataSource;

        private ListenerWrapper(@NonNull Handler handler, @NonNull Listener listener, @NonNull DataSource source) {
            mHandler = handler;
            mListener = listener;
            mDataSource = source;
        }

        @Override
        public void onTranscodeCanceled() {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDataSource.release();
                    mListener.onTranscodeCanceled();
                }
            });
        }

        @Override
        public void onTranscodeCompleted(final int successCode) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDataSource.release();
                    mListener.onTranscodeCompleted(successCode);
                }
            });
        }

        @Override
        public void onTranscodeFailed(@NonNull final Throwable exception) {
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mDataSource.release();
                    mListener.onTranscodeFailed(exception);
                }
            });
        }

        @Override
        public void onTranscodeProgress(final double progress) {
            // Don't think there's a safe way to avoid this allocation?
            // Other than creating a pool of runnables.
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mListener.onTranscodeProgress(progress);
                }
            });
        }
    }
}
