package com.otaliastudios.transcoder.strategy;

import androidx.annotation.NonNull;
import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;

public interface VideoTrackStrategy extends TrackStrategy {

    /**
     * Apply the scaling to the video decoder output
     *
     * It can be done using VideoDecoderOutput.setScale or VideoDecoderOutput.setDrawableScale
     *
     * @param videoDecoderOutput the video decoder output
     * @param scaleX the expected x scaling
     * @param scaleY the expected y scaling
     */
    void scaleOutput(@NonNull VideoDecoderOutput videoDecoderOutput, float scaleX, float scaleY);
}
