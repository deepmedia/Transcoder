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

import android.os.Build;

import com.otaliastudios.transcoder.engine.DefaultEngine;
import com.otaliastudios.transcoder.sink.DataSink;
import com.otaliastudios.transcoder.validator.Validator;

import java.io.FileDescriptor;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

public class Transcoder {
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

    private Transcoder() { /* private */ }

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
     * Starts building transcoder options.
     * Requires a non null fileDescriptor to the output file or stream
     *
     * @param fileDescriptor descriptor of the output file or stream
     * @return an options builder
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    @NonNull
    public static TranscoderOptions.Builder into(@NonNull FileDescriptor fileDescriptor) {
        return new TranscoderOptions.Builder(fileDescriptor);
    }

    /**
     * Starts building transcoder options.
     * Requires a non null sink.
     *
     * @param dataSink the output sink
     * @return an options builder
     */
    @NonNull
    public static TranscoderOptions.Builder into(@NonNull DataSink dataSink) {
        return new TranscoderOptions.Builder(dataSink);
    }

    /**
     * NOTE: A better maximum pool size (instead of CPU+1) would be the number of MediaCodec
     * instances that the device can handle at the same time. Hard to tell though as that
     * also depends on the codec type / on input data.
     */
    private final ThreadPoolExecutor mExecutor = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors() + 1,
            Runtime.getRuntime().availableProcessors() + 1,
            60,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<Runnable>(),
            new ThreadFactory() {
                private final AtomicInteger count = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "TranscoderThread #" + count.getAndIncrement());
                }
            });

    /**
     * Transcodes video file asynchronously.
     *
     * @param options The transcoder options.
     * @return a Future that completes when transcoding is completed
     */
    @NonNull
    public Future<Void> transcode(@NonNull final TranscoderOptions options) {
        return mExecutor.submit(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                new DefaultEngine().transcode(options);
                return null;
            }
        });
    }

}
