package com.otaliastudios.transcoder.strategy;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackStatus;

import java.util.List;

/**
 * An {@link TrackStrategy} that removes this track from output.
 */
@SuppressWarnings("unused")
public class RemoveTrackStrategy implements TrackStrategy {

    @NonNull
    @Override
    public TrackStatus createOutputFormat(@NonNull List<MediaFormat> inputFormats, @NonNull MediaFormat outputFormat) {
        return TrackStatus.REMOVING;
    }
}
