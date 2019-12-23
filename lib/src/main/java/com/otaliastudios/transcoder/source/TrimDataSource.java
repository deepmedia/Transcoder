package com.otaliastudios.transcoder.source;


import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.TrackTypeMap;

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
    private TrackTypeMap<Boolean> readyTracks;

    public TrimDataSource(@NonNull DataSource source, long trimStartUs, long trimEndUs) throws IllegalArgumentException {
        if (trimStartUs < 0 || trimEndUs < 0) {
            throw new IllegalArgumentException("Trim values cannot be negative.");
        }
        this.source = source;
        this.trimStartUs = trimStartUs;
        this.trimDurationUs = computeTrimDuration(source.getDurationUs(), trimStartUs, trimEndUs);
        this.readyTracks = new TrackTypeMap<>(!hasTrack(TrackType.VIDEO) || trimStartUs == 0,
                !hasTrack(TrackType.AUDIO) || trimStartUs == 0);
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
        final MediaFormat trackFormat = source.getTrackFormat(type);
        if (trackFormat != null) {
            trackFormat.setLong(MediaFormat.KEY_DURATION, trimDurationUs);
        }
        return trackFormat;
    }

    private boolean hasTrack(@NonNull TrackType type) {
        return source.getTrackFormat(type) != null;
    }

    @Override
    public void selectTrack(@NonNull TrackType type) {
        source.selectTrack(type);
    }

    @Override
    public long seekTo(long timestampUs) {
        return source.seekTo(timestampUs);
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        if (source.canReadTrack(type)) {
            if (readyTracks.requireAudio() && readyTracks.requireVideo()) {
                return true;
            }
            switch (type) {
                case AUDIO:
                    if (!readyTracks.requireAudio()) {
                        final long sampleTimeUs = seekTo(trimStartUs);
                        updateTrimValues(sampleTimeUs);
                        readyTracks.setAudio(true);
                    }
                    return readyTracks.requireVideo();
                case VIDEO:
                    if (!readyTracks.requireVideo()) {
                        final long sampleTimeUs = seekTo(trimStartUs);
                        updateTrimValues(sampleTimeUs);
                        readyTracks.setVideo(true);
                        if (readyTracks.requireAudio()) {
                            // Seeking a second time helps the extractor with Audio sampleTime issues
                            seekTo(trimStartUs);
                        }
                    }
                    return readyTracks.requireAudio();
            }
        }
        return false;
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
        return source.isDrained();
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        switch (type) {
            case AUDIO:
                readyTracks.setAudio(false);
                break;
            case VIDEO:
                readyTracks.setVideo(false);
                break;
        }
        source.releaseTrack(type);
    }

    @Override
    public void rewind() {
        readyTracks.setAudio(false);
        readyTracks.setVideo(false);
        source.rewind();
    }
}
