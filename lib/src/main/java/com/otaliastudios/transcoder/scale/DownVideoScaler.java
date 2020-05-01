package com.otaliastudios.transcoder.scale;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;

/**
 * An {@link VideoScaler} that scale the video down so that no side exceed the new resolution
 * Sides that are too small will have black borders
 */
public class DownVideoScaler implements VideoScaler {
    @Override
    public void scaleOutput(@NonNull VideoDecoderOutput videoDecoderOutput, float scaleX, float scaleY, boolean flipped) {
        if (flipped) { // The drawable is not affected by the flip so we need to reverse it
            videoDecoderOutput.setDrawableScale(1.0F / scaleX, 1.0F / scaleY);
        } else {
            videoDecoderOutput.setDrawableScale(1.0F / scaleY, 1.0F / scaleX);
        }
    }
}