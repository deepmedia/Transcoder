package com.otaliastudios.transcoder.strategy;

import android.media.MediaFormat;

import com.otaliastudios.transcoder.strategy.size.Resizer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base class for video/audio format strategy.
 * Video strategies should use a {@link Resizer} instance to compute the output
 * video size.
 */
public interface TrackStrategy {

    /**
     * Create the output format for this track (either audio or video).
     * Implementors can:
     * - throw a {@link TrackStrategyException} if the whole transcoding should be aborted
     * - return {@code inputFormat} for remuxing this track as-is
     * - returning {@code null} for removing this track from output
     *
     * @param inputFormat the input format
     * @return the output format
     */
    @Nullable
    MediaFormat createOutputFormat(@NonNull MediaFormat inputFormat) throws TrackStrategyException;
}
