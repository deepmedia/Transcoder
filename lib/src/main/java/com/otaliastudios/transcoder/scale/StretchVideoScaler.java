package com.otaliastudios.transcoder.scale;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;

/**
 * An {@link VideoScaler} that strech the video so that they match the video resolution
 * at the cost of deforming the images
 */
public class StretchVideoScaler implements VideoScaler {
    @Override
    public void scaleOutput(@NonNull VideoDecoderOutput videoDecoderOutput, float scaleX, float scaleY, boolean flipped) {
        //No scaling will automatically stretch the frames to fill all the drawable space
    }
}