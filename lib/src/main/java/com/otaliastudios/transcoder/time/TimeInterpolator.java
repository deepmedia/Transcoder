package com.otaliastudios.transcoder.time;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.TrackType;

/**
 * An interface to redefine the time between video or audio frames.
 */
public interface TimeInterpolator {

    /**
     * Given the track type (audio or video) and the frame timestamp in microseconds,
     * should return the corrected timestamp.
     *
     * @param type track type
     * @param time frame timestamp in microseconds
     * @return the new frame timestamp
     */
    long interpolate(@NonNull TrackType type, long time);
}
