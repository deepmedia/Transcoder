package com.otaliastudios.transcoder.scale;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;

/**
 * Scale the frames when they are not of the expected resolution
 */
public interface VideoScaler {
    /**
     * Apply the scaling to the video decoder output
     *
     * It can be done using VideoDecoderOutput.setScale or VideoDecoderOutput.setDrawableScale
     *
     * @param videoDecoderOutput the video decoder output
     * @param scaleX the input width/height
     * @param scaleY the output width/height
     * @param flipped whether or not the frame was rotated by 90 degrees
     */
    void scaleOutput(@NonNull VideoDecoderOutput videoDecoderOutput, float scaleX, float scaleY, boolean flipped);
}
