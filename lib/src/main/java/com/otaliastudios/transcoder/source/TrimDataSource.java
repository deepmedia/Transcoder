package com.otaliastudios.transcoder.source;


import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;

import org.jetbrains.annotations.Contract;

/**
 * A {@link DataSource} wrapper that trims source at both ends.
 */
public class TrimDataSource implements DataSource {
    private static final String TAG = "TrimDataSource";
    private static final Logger LOG = new Logger(TAG);
    @NonNull
    private DataSource source;
    private long trimStartUs;
    private long trimDurationUs;
    private boolean didSeekTracks = false;

    public TrimDataSource(@NonNull DataSource source, long trimStartUs, long trimEndUs) throws IllegalArgumentException {
        if (trimStartUs < 0 || trimEndUs < 0) {
            throw new IllegalArgumentException("Trim values cannot be negative.");
        }
        this.source = source;
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
    public int getOrientation() {
        return source.getOrientation();
    }

    @Nullable
    @Override
    public double[] getLocation() {
        return source.getLocation();
    }

    @Override
    public long getDurationUs() {
        return trimDurationUs;
    }

    @Nullable
    @Override
    public MediaFormat getTrackFormat(@NonNull TrackType type) {
        return source.getTrackFormat(type);
    }

    @Override
    public void selectTrack(@NonNull TrackType type) {
        source.selectTrack(type);
    }

    @Override
    public long seekBy(long durationUs) {
        return source.seekBy(durationUs);
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        if (!didSeekTracks) {
            final long sampleTimeUs = seekBy(trimStartUs);
            updateTrimValues(sampleTimeUs);
            didSeekTracks = true;
        }
        return source.canReadTrack(type);
    }

    private void updateTrimValues(long timestampUs) {
        trimDurationUs += trimStartUs - timestampUs;
        trimStartUs = timestampUs;
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        source.readTrack(chunk);
    }

    @Override
    public long getReadUs() {
        return source.getReadUs();
    }

    @Override
    public boolean isDrained() {
        return source.isDrained() || getReadUs() >= getDurationUs();
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        source.releaseTrack(type);
    }

    @Override
    public void rewind() {
        didSeekTracks = false;
        source.rewind();
    }
}
