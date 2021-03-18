package com.otaliastudios.transcoder.internal.transcode;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.TranscoderListener;
import com.otaliastudios.transcoder.TranscoderOptions;

/**
 * Wraps a TranscoderListener and posts events on the given handler.
 */
class TranscodeDispatcher {

    private final Handler mHandler;
    private final TranscoderListener mListener;

    TranscodeDispatcher(@NonNull TranscoderOptions options) {
        mHandler = options.getListenerHandler();
        mListener = options.getListener();
    }

    void dispatchCancel() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeCanceled();
            }
        });
    }

    void dispatchSuccess(final int successCode) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeCompleted(successCode);
            }
        });
    }

    void dispatchFailure(@NonNull final Throwable exception) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeFailed(exception);
            }
        });
    }

    void dispatchProgress(final double progress) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onTranscodeProgress(progress);
            }
        });
    }
}
