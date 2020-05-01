package com.otaliastudios.transcoder.scale;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;

/**
 * An {@link VideoScaler} that scale the video up so that it touches all sides
 * of the new resolution and exceeding parts will be truncated
 */
public class UpVideoScaler implements VideoScaler {
    @Override
    public void scaleOutput(@NonNull VideoDecoderOutput videoDecoderOutput, float scaleX, float scaleY, boolean flipped) {
        videoDecoderOutput.setScale(scaleX, scaleY);
    }
}
