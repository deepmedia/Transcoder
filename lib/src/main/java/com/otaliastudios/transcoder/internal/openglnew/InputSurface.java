package com.otaliastudios.transcoder.internal.openglnew;


import android.opengl.EGL14;
import android.view.Surface;

import androidx.annotation.NonNull;

import com.otaliastudios.opengl.core.EglCore;
import com.otaliastudios.opengl.surface.EglWindowSurface;

/**
 * The constructor takes a Surface obtained from MediaCodec.createInputSurface(), and uses that
 * to create an EGL window surface.  Calls to eglSwapBuffers() cause a frame of data to be sent
 * to the video encoder.
 */
public class InputSurface {
    @SuppressWarnings("unused")
    private static final String TAG = InputSurface.class.getSimpleName();

    private EglCore mEglCore;
    private EglWindowSurface mEglSurface;

    /**
     * Creates an InputSurface from a Surface.
     * @param surface the surface
     */
    public InputSurface(@NonNull Surface surface) {
        mEglCore = new EglCore(EGL14.EGL_NO_CONTEXT, EglCore.FLAG_RECORDABLE);
        mEglSurface = new EglWindowSurface(mEglCore, surface, true);
    }

    public void makeCurrent() {
        mEglSurface.makeCurrent();
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean swapBuffers() {
        return mEglSurface.swapBuffers();
    }

    public void setPresentationTime(long nsec) {
        mEglSurface.setPresentationTime(nsec);
    }

    public void release() {
        // NOTE: Original code calls android.view.Surface.release()
        // after the egl core releasing. This should not be an issue.
        mEglSurface.release();
        mEglCore.release();
    }
}
