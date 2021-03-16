package com.otaliastudios.transcoder.source;


import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.common.TrackType;
import com.otaliastudios.transcoder.internal.utils.Logger;

/**
 * A {@link DataSource} that trims the inner source at both ends.
 */
public class TrimDataSource extends DataSourceWrapper {
    private static final Logger LOG = new Logger("TrimDataSource");

    private final long trimStartUs;
    private final long trimEndUs;

    private long extraDurationUs;
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
            LOG.w("Trim values are too large! " +
                    "start=" + trimStartUs + ", " +
                    "end=" + trimEndUs + ", " +
                    "duration=" + duration);
            throw new IllegalArgumentException(
                    "Trim values cannot be greater than media duration.");
        }
        trimDurationUs = duration - trimStartUs - trimEndUs;
    }

    @Override
    public long getDurationUs() {
        return trimDurationUs + extraDurationUs;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        // Seek lazily here instead of in initialize, so that it is done once all
        // tracks have been selected.
        if (!trimDone && trimStartUs > 0) {
            extraDurationUs = trimStartUs - getSource().seekTo(trimStartUs);
            trimDone = true;
        }
        return super.canReadTrack(type);
    }

    @Override
    public boolean isDrained() {
        // Enforce the trim end: this works thanks to the fact that extraDurationUs is added
        // to the duration, otherwise it would fail for videos with sparse keyframes.
        return super.isDrained() || getReadUs() >= getDurationUs();
    }

    @Override
    public long seekTo(long desiredPositionUs) {
        // our 0 is the wrapped source's trimStartUs
        // TODO should we update extraDurationUs?
        long superDesiredUs = trimStartUs + desiredPositionUs;
        long superReceivedUs = getSource().seekTo(superDesiredUs);
        return superReceivedUs - trimStartUs;
    }

    @Override
    public void deinitialize() {
        super.deinitialize();
        trimDone = false;
    }
}
