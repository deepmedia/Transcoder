package com.otaliastudios.transcoder.source;


import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.TrackType;
import com.otaliastudios.transcoder.internal.utils.Logger;

/**
 * A {@link DataSource} that trims the inner source at both ends.
 */
public class TrimDataSource extends DataSourceWrapper {
    private static final Logger LOG = new Logger("TrimDataSource");

    private long trimStartUs;
    private final long trimEndUs;

    private long trimDurationUs;
    private boolean trimDone = false;

    @SuppressWarnings("WeakerAccess")
    public TrimDataSource(@NonNull DataSource source, long trimStartUs) {
        this(source, trimStartUs, 0);
    }

    @SuppressWarnings("WeakerAccess")
    public TrimDataSource(@NonNull DataSource source, long trimStartUs, long trimEndUs) {
        super(source);
        if (trimStartUs < 0 || trimEndUs < 0) {
            throw new IllegalArgumentException("Trim values cannot be negative.");
        }
        this.trimStartUs = trimStartUs;
        this.trimEndUs = trimEndUs;
    }

    @Override
    public void initialize() {
        super.initialize();
        long duration = getSource().getDurationUs();
        if (trimStartUs + trimEndUs >= duration) {
            LOG.w("Trim values are too large! start=" +
                    trimStartUs + ", end=" +
                    trimEndUs + ", duration=" + duration);
            throw new IllegalArgumentException(
                    "Trim values cannot be greater than media duration.");
        }
        trimDurationUs = duration - trimStartUs - trimEndUs;
    }

    @Override
    public long getDurationUs() {
        return trimDurationUs;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        if (!trimDone && trimStartUs > 0) {
            trimStartUs = getSource().seekTo(trimStartUs);
            trimDone = true;
        }
        return super.canReadTrack(type);
    }

    @Override
    public boolean isDrained() {
        return super.isDrained() || getReadUs() >= getDurationUs();
    }

    @Override
    public long seekTo(long durationUs) {
        // our 0 is the wrapped source's trimStartUs
        long result = super.seekTo(trimStartUs + durationUs);
        return result - trimStartUs;
    }

    @Override
    public void deinitialize() {
        super.deinitialize();
        trimDone = false;
    }
}
