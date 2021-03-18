package com.otaliastudios.transcoder.internal.thumbnails;

import android.os.Handler;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.ThumbnailerListener;
import com.otaliastudios.transcoder.ThumbnailerOptions;
import com.otaliastudios.transcoder.thumbnail.Thumbnail;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps a ThumbnailerListener and posts events on the given handler.
 */
class ThumbnailsDispatcher {

    private final Handler mHandler;
    private final ThumbnailerListener mListener;
    private final List<Thumbnail> mResults = new ArrayList<>();

    ThumbnailsDispatcher(@NonNull ThumbnailerOptions options) {
        mHandler = options.getListenerHandler();
        mListener = options.getListener();
    }

    void dispatchCancel() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onThumbnailsCanceled();
            }
        });
    }

    void dispatchCompletion() {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onThumbnailsCompleted(mResults);
            }
        });
    }

    void dispatchFailure(@NonNull final Throwable exception) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onThumbnailsFailed(exception);
            }
        });
    }

    void dispatchThumbnail(@NonNull final Thumbnail thumbnail) {
        mResults.add(thumbnail);
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                mListener.onThumbnail(thumbnail);
            }
        });
    }
}
