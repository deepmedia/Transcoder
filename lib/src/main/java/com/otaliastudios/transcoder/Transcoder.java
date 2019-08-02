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

import com.otaliastudios.transcoder.engine.TranscoderEngine;
import com.otaliastudios.transcoder.source.DataSource;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.validator.Validator;
import com.otaliastudios.transcoder.engine.ValidatorException;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;

public class Transcoder {
    private static final String TAG = Transcoder.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    /**
     * Constant for {@link TranscoderListener#onTranscodeCompleted(int)}.
     * Transcoding was executed successfully.
     */
    public static final int SUCCESS_TRANSCODED = 0;

    /**
     * Constant for {@link TranscoderListener#onTranscodeCompleted(int)}:
     * transcoding was not executed because it was considered
     * not necessary by the {@link Validator}.
     */
    public static final int SUCCESS_NOT_NEEDED = 1;

    private static volatile Transcoder sTranscoder;

    private class Factory implements ThreadFactory {
        private AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(@NonNull Runnable runnable) {
            return new Thread(runnable, TAG + " Thread #" + count.getAndIncrement());
        }
    }

    private ThreadPoolExecutor mExecutor;

    private Transcoder() {
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
    public static Transcoder getInstance() {
        if (sTranscoder == null) {
            synchronized (Transcoder.class) {
                if (sTranscoder == null) {
                    sTranscoder = new Transcoder();
                }
            }
        }
        return sTranscoder;
    }

    /**
     * Starts building transcoder options.
     * Requires a non null absolute path to the output file.
     *
     * @param outPath path to output file
     * @return an options builder
     */
    @NonNull
    public static TranscoderOptions.Builder into(@NonNull String outPath) {
        return new TranscoderOptions.Builder(outPath);
    }

    /**
     * Transcodes video file asynchronously.
     *
     * @param options The transcoder options.
     * @return a Future that completes when transcoding is completed
     */
    @NonNull
    public Future<Void> transcode(@NonNull final TranscoderOptions options) {
        final TranscoderListener listenerWrapper = new ListenerWrapper(options.listenerHandler,
                options.listener, options.getDataSource());
        return mExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    TranscoderEngine engine = new TranscoderEngine(options.getDataSource(), new TranscoderEngine.ProgressCallback() {
                        @Override
                        public void onProgress(final double progress) {
                            listenerWrapper.onTranscodeProgress(progress);
                        }
                    });
                    engine.transcode(options);
                    listenerWrapper.onTranscodeCompleted(SUCCESS_TRANSCODED);

                } catch (ValidatorException e) {
                    LOG.i("Validator has decided that the input is fine and transcoding is not necessary.");
                    listenerWrapper.onTranscodeCompleted(SUCCESS_NOT_NEEDED);

                } catch (Throwable e) {
                    // Check InterruptedException in e and in its causes.
                    Throwable current = e;
                    boolean isInterrupted = e instanceof InterruptedException;
                    while (!isInterrupted && current.getCause() != null && !current.getCause().equals(current)) {
                        current = current.getCause();
                        if (current instanceof InterruptedException) isInterrupted = true;
                    }
                    if (isInterrupted) {
                        LOG.i("Transcode canceled.", current);
                        listenerWrapper.onTranscodeCanceled();

                    } else if (e instanceof RuntimeException) {
                        LOG.e("Fatal error while transcoding, this might be invalid format or bug in engine or Android.", e);
                        listenerWrapper.onTranscodeFailed(e);
                        throw e;

                    } else {
                        LOG.e("Unexpected error while transcoding", e);
                        listenerWrapper.onTranscodeFailed(e);
                        throw e;
                    }
                }
                return null;
            }
        });
    }

    /**
     * Wraps a TranscoderListener and a DataSource object, ensuring that the source
     * is released when transcoding ends, fails or is canceled.
     *
     * It posts events on the given handler.
     */
    private static class ListenerWrapper implements TranscoderListener {

        private Handler mHandler;
        private TranscoderListener mListener;
        private DataSource mDataSource;

        private ListenerWrapper(@NonNull Handler handler, @NonNull TranscoderListener listener, @NonNull DataSource source) {
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
