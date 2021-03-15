package com.otaliastudios.transcoder.transcode.internal;


import android.media.MediaCodec;
import android.opengl.EGL14;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.otaliastudios.opengl.core.EglCore;
import com.otaliastudios.opengl.surface.EglWindowSurface;

/**
 * The purpose of this class is basically doing OpenGL initialization.
 *
 * This class takes as input a Surface obtained from {@link MediaCodec#createInputSurface()},
 * and uses that to create an EGL context and window surface.
 *
 * Calls to {@link #onFrame(long)} cause a frame of data to be sent to the surface, thus
 * to the {@link android.media.MediaCodec} input.
 */
public class FramePublisher {

    private final EglCore mEglCore;
    private final EglWindowSurface mEglSurface;

    /**
     * Creates an VideoEncoderInput from a Surface.
     * Makes the EGL surface current immediately.
     * @param surface the surface
     */
    public FramePublisher(@NonNull Surface surface) {
        mEglCore = new EglCore(EGL14.EGL_NO_CONTEXT, EglCore.FLAG_RECORDABLE);
        mEglSurface = new EglWindowSurface(mEglCore, surface, true);
        mEglSurface.makeCurrent();
    }

    public void onFrame(long presentationTimeUs) {
        mEglSurface.setPresentationTime(presentationTimeUs * 1000L);
        mEglSurface.swapBuffers();
    }

    public void release() {
        // NOTE: Original code calls android.view.Surface.release()
        // after the egl core releasing. This should not be an issue.
        mEglSurface.release();
        mEglCore.release();
    }
}
