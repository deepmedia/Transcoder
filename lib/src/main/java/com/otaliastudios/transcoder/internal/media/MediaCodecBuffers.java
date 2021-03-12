package com.otaliastudios.transcoder.internal.media;

import android.media.MediaCodec;
import android.os.Build;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

/**
 * A Wrapper to MediaCodec that facilitates the use of API-dependent get{Input/Output}Buffer methods,
 * in order to prevent: http://stackoverflow.com/q/30646885
 */
public class MediaCodecBuffers {

    private final MediaCodec mMediaCodec;
    private final ByteBuffer[] mInputBuffers;
    private ByteBuffer[] mOutputBuffers;

    public MediaCodecBuffers(@NonNull MediaCodec mediaCodec) {
        mMediaCodec = mediaCodec;

        if (Build.VERSION.SDK_INT < 21) {
            mInputBuffers = mediaCodec.getInputBuffers();
            mOutputBuffers = mediaCodec.getOutputBuffers();
        } else {
            mInputBuffers = mOutputBuffers = null;
        }
    }

    @NonNull
    public ByteBuffer getInputBuffer(final int index) {
        if (Build.VERSION.SDK_INT >= 21) {
            // This is nullable only for incorrect usage.
            return mMediaCodec.getInputBuffer(index);
        }
        ByteBuffer result = mInputBuffers[index];
        result.clear();
        return result;
    }

    @NonNull
    public ByteBuffer getOutputBuffer(final int index) {
        if (Build.VERSION.SDK_INT >= 21) {
            // This is nullable only for incorrect usage.
            return mMediaCodec.getOutputBuffer(index);
        }
        return mOutputBuffers[index];
    }

    public void onOutputBuffersChanged() {
        if (Build.VERSION.SDK_INT < 21) {
            mOutputBuffers = mMediaCodec.getOutputBuffers();
        }
    }
}
