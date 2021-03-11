package com.otaliastudios.transcoder.strategy;

import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.TrackStatus;

import java.util.List;

/**
 * An {@link TrackStrategy} that asks the encoder to keep this track as is.
 * Note that this is risky, as the track type might not be supported by
 * the mp4 container.
 */
@SuppressWarnings("unused")
public class PassThroughTrackStrategy implements TrackStrategy {

    @NonNull
    @Override
    public TrackStatus createOutputFormat(@NonNull List<MediaFormat> inputFormats, @NonNull MediaFormat outputFormat) {
        return TrackStatus.PASS_THROUGH;
    }
}
