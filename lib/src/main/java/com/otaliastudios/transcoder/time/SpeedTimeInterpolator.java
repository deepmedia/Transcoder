package com.otaliastudios.transcoder.time;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;

/**
 * A {@link TimeInterpolator} that modifies the playback speed by the given
 * float factor. A factor less than 1 will slow down, while a bigger factor will
 * accelerate.
 */
public class SpeedTimeInterpolator implements TimeInterpolator {

    private float mFactor;

    /**
     * Creates a new speed interpolator for the given factor.
     * Throws if factor is less than 0 or equal to 0.
     * @param factor a factor
     */
    public SpeedTimeInterpolator(float factor) {
        if (factor <= 0) {
            throw new IllegalArgumentException("Invalid speed factor: " + factor);
        }
        mFactor = factor;
    }

    @Override
    public long interpolate(@NonNull TrackType type, long time) {
        return time;
    }
}
