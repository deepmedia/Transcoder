package com.otaliastudios.transcoder.validator;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackStatus;

/**
 * A validator determines if the transcoding process should proceed or not,
 * after the {@link com.otaliastudios.transcoder.strategy.OutputStrategy} have
 * provided the output format.
 */
public interface Validator {

    /**
     * Return true if the transcoding should proceed, false otherwise.
     *
     * @param videoStatus the status of the video track
     * @param audioStatus the status of the audio track
     * @return true to proceed
     */
    boolean validate(@NonNull TrackStatus videoStatus, @NonNull TrackStatus audioStatus);

}
