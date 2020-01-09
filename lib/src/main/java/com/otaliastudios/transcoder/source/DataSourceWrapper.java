package com.otaliastudios.transcoder.source;


import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;

/**
 * A {@link DataSource} wrapper that simply delegates all methods to the
 * wrapped source.
 */
public class DataSourceWrapper implements DataSource {

    private final DataSource mSource;

    @SuppressWarnings("WeakerAccess")
    protected DataSourceWrapper(@NonNull DataSource source) {
        mSource = source;
    }

    @NonNull
    protected DataSource getSource() {
        return mSource;
    }

    @Override
    public int getOrientation() {
        return mSource.getOrientation();
    }

    @Nullable
    @Override
    public double[] getLocation() {
        return mSource.getLocation();
    }

    @Override
    public long getDurationUs() {
        return mSource.getDurationUs();
    }

    @Nullable
    @Override
    public MediaFormat getTrackFormat(@NonNull TrackType type) {
        return mSource.getTrackFormat(type);
    }

    @Override
    public void selectTrack(@NonNull TrackType type) {
        mSource.selectTrack(type);
    }

    @Override
    public long seekTo(long desiredTimestampUs) {
        return mSource.seekTo(desiredTimestampUs);
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        return mSource.canReadTrack(type);
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        mSource.readTrack(chunk);
    }

    @Override
    public long getReadUs() {
        return mSource.getReadUs();
    }

    @Override
    public boolean isDrained() {
        return mSource.isDrained();
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        mSource.releaseTrack(type);
    }

    @Override
    public void rewind() {
        mSource.rewind();
    }
}
