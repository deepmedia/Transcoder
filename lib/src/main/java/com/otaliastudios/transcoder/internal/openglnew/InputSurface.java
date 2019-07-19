/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
// from: https://android.googlesource.com/platform/cts/+/lollipop-release/tests/tests/media/src/android/media/cts/InputSurface.java
// blob: 157ed88d143229e4edb6889daf18fb73aa2fc5a5
package com.otaliastudios.transcoder.internal.openglnew;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
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
