package com.otaliastudios.transcoder.time;

import android.util.Log;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;

import java.util.HashMap;
import java.util.Map;

/**
 * A {@link TimeInterpolator} that modifies the playback speed by the given
 * float factor. A factor less than 1 will slow down, while a bigger factor will
 * accelerate.
 */
public class SpeedTimeInterpolator implements TimeInterpolator {

    private double mFactor;
    private final Map<TrackType, TrackData> mTrackData = new HashMap<>();

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

    /**
     * Returns the factor passed to the constructor.
     * @return the factor
     */
    @SuppressWarnings("unused")
    public float getFactor() {
        return (float) mFactor;
    }

    @Override
    public long interpolate(@NonNull TrackType type, long time) {
        if (!mTrackData.containsKey(type)) {
            mTrackData.put(type, new TrackData());
        }
        TrackData data = mTrackData.get(type);
        //noinspection ConstantConditions
        if (data.lastRealTime == Long.MIN_VALUE) {
            data.lastRealTime = time;
            data.lastCorrectedTime = time;
        } else {
            long realDelta = time - data.lastRealTime;
            long correctedDelta = (long) ((double) realDelta / mFactor);
            data.lastRealTime = time;
            data.lastCorrectedTime += correctedDelta;
        }
        Log.e("SpeedTimeInterpolator", "Input time: " + time + ", output time: " + data.lastCorrectedTime);
        return data.lastCorrectedTime;
    }

    private static class TrackData {
        private long lastRealTime = Long.MIN_VALUE;
        private long lastCorrectedTime = Long.MIN_VALUE;
    }
}
