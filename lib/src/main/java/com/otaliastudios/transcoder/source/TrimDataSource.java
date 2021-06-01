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

    private long extraDurationUs = 0;
    private long trimDurationUs = Long.MIN_VALUE;
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
    public boolean isInitialized() {
        return super.isInitialized() && trimDurationUs != Long.MIN_VALUE;
    }

    @Override
    public void deinitialize() {
        super.deinitialize();
        trimDurationUs = Long.MIN_VALUE;
        trimDone = false;
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
        LOG.i("initialize(): duration=" + duration
                + " trimStart=" + trimStartUs
                + " trimEnd=" + trimEndUs
                + " trimDuration=" + (duration - trimStartUs - trimEndUs));
        trimDurationUs = duration - trimStartUs - trimEndUs;
    }

    // Assuming e.g. video of 8 seconds, trimStart=2s, trimEnd=1s.
    // The trimmed duration is 5 seconds. Assume also that the video has sparse keyframes
    // so that when we do seekTo(2s), it returns 0 because it couldn't seek there. We have two options:
    // - duration=5s, position goes from -2s to 5s
    // - duration=7s, position goes from 0s to 7s
    // What we choose is not so important, as long as we use the same approach in getDurationUs
    // and getPositionUs consistently. Approach 1 would be easier (no extra) but also weird.

    @Override
    public long getDurationUs() {
        return trimDurationUs + extraDurationUs;
    }
    
    @Override
    public long getPositionUs() {
        return super.getPositionUs() - trimStartUs + extraDurationUs;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        // Seek lazily here instead of in initialize, so that it is done once all
        // tracks have been selected.
        if (!trimDone && trimStartUs > 0) {
            extraDurationUs = trimStartUs - getSource().seekTo(trimStartUs);
            LOG.i("canReadTrack(): extraDurationUs=" + extraDurationUs
                    + " trimStartUs=" + trimStartUs
                    + " source.seekTo(trimStartUs)=" + (extraDurationUs - trimStartUs));
            trimDone = true;
        }
        return super.canReadTrack(type);
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        super.readTrack(chunk);
        chunk.timeUs = chunk.timeUs - trimStartUs;
    }

    @Override
    public boolean isDrained() {
        // Enforce the trim end: this works thanks to the fact that extraDurationUs is added
        // to the duration, otherwise it would fail for videos with sparse keyframes.
        return super.isDrained() || getPositionUs() >= getDurationUs();
    }

    @Override
    public long seekTo(long desiredPositionUs) {
        // our 0 is the wrapped source's trimStartUs
        // TODO should we update extraDurationUs?
        long superDesiredUs = trimStartUs + desiredPositionUs;
        long superReceivedUs = getSource().seekTo(superDesiredUs);
        return superReceivedUs - trimStartUs;
    }
}
