package com.otaliastudios.transcoder.time;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.TrackType;

/**
 * A {@link TimeInterpolator} that does no time interpolation or correction -
 * it just returns the input time.
 */
public class DefaultTimeInterpolator implements TimeInterpolator {

    @Override
    public long interpolate(@NonNull TrackType type, long time) {
        return time;
    }
}
