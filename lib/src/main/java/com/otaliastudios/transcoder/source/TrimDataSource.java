package com.otaliastudios.transcoder.source;


import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;

import org.jetbrains.annotations.Contract;

/**
 * A {@link DataSourceWrapper} that trims source at both ends.
 */
public class TrimDataSource extends DataSourceWrapper {
    private static final String TAG = "TrimDataSource";
    private static final Logger LOG = new Logger(TAG);
    private long trimStartUs;
    private long trimDurationUs;
    private boolean didSeekTracks = false;

    public TrimDataSource(@NonNull DataSource source, long trimStartUs, long trimEndUs) throws IllegalArgumentException {
        super(source);
        if (trimStartUs < 0 || trimEndUs < 0) {
            throw new IllegalArgumentException("Trim values cannot be negative.");
        }
        this.trimStartUs = trimStartUs;
        this.trimDurationUs = computeTrimDuration(source.getDurationUs(), trimStartUs, trimEndUs);
    }

    @Contract(pure = true)
    private static long computeTrimDuration(long duration, long trimStart, long trimEnd) throws IllegalArgumentException {
        if (trimStart + trimEnd > duration) {
            throw new IllegalArgumentException("Trim values cannot be greater than media duration.");
        }
        return duration - trimStart - trimEnd;
    }

    @Override
    public long getDurationUs() {
        return trimDurationUs;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        if (!didSeekTracks) {
            final long sampleTimeUs = seekBy(trimStartUs);
            updateTrimValues(sampleTimeUs);
            didSeekTracks = true;
        }
        return super.canReadTrack(type);
    }

    private void updateTrimValues(long timestampUs) {
        trimDurationUs += trimStartUs - timestampUs;
        trimStartUs = timestampUs;
    }

    @Override
    public boolean isDrained() {
        return super.isDrained() || getReadUs() >= getDurationUs();
    }

    @Override
    public void rewind() {
        super.rewind();
        didSeekTracks = false;
    }
}
