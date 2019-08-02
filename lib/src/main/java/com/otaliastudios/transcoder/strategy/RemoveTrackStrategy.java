package com.otaliastudios.transcoder.strategy;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * An {@link TrackStrategy} that removes this track from output.
 */
@SuppressWarnings("unused")
public class RemoveTrackStrategy implements TrackStrategy {

    @Nullable
    @Override
    public MediaFormat createOutputFormat(@NonNull MediaFormat inputFormat) throws TrackStrategyException {
        return null;
    }
}
