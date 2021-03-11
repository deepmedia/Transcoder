package com.otaliastudios.transcoder.engine;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.TranscoderListener;

/**
 * Wraps a TranscoderListener and posts events on the given handler.
 */
public class Dispatcher implements TranscoderListener {

    private final Handler mHandler;
    private final TranscoderListener mListener;

    Dispatcher(@NonNull Handler handler, @NonNull TranscoderListener listener) {
        mHandler = handler;
        mListener = listener;
    }

    @Override
    public void onTranscodeCanceled() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeCanceled();
            }
        });
    }

    @Override
    public void onTranscodeCompleted(final int successCode) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeCompleted(successCode);
            }
        });
    }

    @Override
    public void onTranscodeFailed(@NonNull final Throwable exception) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeFailed(exception);
            }
        });
    }

    @Override
    public void onTranscodeProgress(final double progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeProgress(progress);
            }
        });
    }
}
