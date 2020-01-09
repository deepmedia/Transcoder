package com.otaliastudios.transcoder.source;


import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.TrackTypeMap;

import org.jetbrains.annotations.Contract;

/**
 * A {@link DataSourceWrapper} that trims source at both ends.
 */
public class TrimDataSource extends DataSourceWrapper {
    private static final String TAG = TrimDataSource.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    private long trimStartUs;
    private long trimDurationUs;
    private final TrackTypeMap<Boolean> trimDone
            = new TrackTypeMap<>(false, false);
    private boolean a;


    public TrimDataSource(@NonNull DataSource source, long trimStartUs, long trimEndUs) {
        super(source);
        if (trimStartUs < 0 || trimEndUs < 0) {
            throw new IllegalArgumentException("Trim values cannot be negative.");
        }
        long duration = source.getDurationUs();
        if (trimStartUs + trimEndUs >= duration) {
            throw new IllegalArgumentException(
                    "Trim values cannot be greater than media duration.");
        }
        this.trimStartUs = trimStartUs;
        this.trimDurationUs = duration - trimStartUs - trimEndUs;
    }

    @Override
    public long getDurationUs() {
        return trimDurationUs;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        if (!a && trimStartUs > 0) {
            // We must seek the inner source to the correct position. We do this
            // once per track, for two reasons:
            // 1. MediaExtractor is not really good at seeking with some files
            // 2. MediaExtractor only seeks selected tracks, and we're not sure
            // that all tracks are selected when this method is called.
            seekTo(0);
            trimDone.set(type, true);
            a = true;
        }
        return super.canReadTrack(type);
    }

    @Override
    public boolean isDrained() {
        return super.isDrained() || getReadUs() >= getDurationUs();
    }

    @Override
    public void seekTo(long durationUs) {
        // our 0 is the wrapped source's trimStartUs
        super.seekTo(trimStartUs + durationUs);
    }

    @Override
    public void rewind() {
        super.rewind();
        trimDone.setVideo(false);
        trimDone.setAudio(false);
    }
}
