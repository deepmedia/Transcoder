package com.otaliastudios.transcoder.transcode.base;

import android.media.MediaFormat;
import android.view.Surface;

import androidx.annotation.NonNull;

public interface VideoDecoderOutputBase {


    /**
     * Configures rotation and scale for given formats.
     * @param inputFormat input format
     * @param outputFormat output format
     * @param sourceRotation source rotation
     * @param extraRotation extra rotation
     */
    void configureWith(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat, int sourceRotation, int extraRotation);


    /**
     * Returns a Surface to draw onto.
     * @return the output surface
     */
    Surface getSurface();

    /**
     * Waits for a new frame drawn into our surface (see {@link #getSurface()}),
     * then draws it using OpenGL.
     */
    void drawFrame();

    /**
     * Discard all resources held by this class, notably the EGL context.
     */
    void release();
}
