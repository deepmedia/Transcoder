package com.otaliastudios.transcoder.source;


import android.media.MediaExtractor;
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
    private final boolean hasVideoTrack;
    @NonNull
    private MediaExtractorDataSource source;
    private long trimStartUs;
    private long trimDurationUs;
    private boolean isSeekTrackReady = false;
    private boolean hasSelectedVideoTrack = false;

    public TrimDataSource(@NonNull MediaExtractorDataSource source, long trimStartUs, long trimEndUs) {
        if (trimStartUs < 0 || trimEndUs < 0) {
            throw new IllegalArgumentException("Trim values cannot be negative.");
        }
        this.source = source;
        this.trimStartUs = trimStartUs;
        this.trimDurationUs = computeTrimDuration(source.getDurationUs(), trimStartUs, trimEndUs);
        this.hasVideoTrack = source.getTrackFormat(TrackType.VIDEO) != null;
    }

    @Contract(pure = true)
    private static long computeTrimDuration(long duration, long trimStart, long trimEnd) {
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

    @Override
    public void selectTrack(@NonNull TrackType type) {
        if (trimStartUs > 0) {
            switch (type) {
                case AUDIO:
                    if (hasVideoTrack && !hasSelectedVideoTrack) {
                        selectAndSeekVideoTrack();
                    }
                    source.selectTrack(TrackType.AUDIO);
                    break;
                case VIDEO:
                    if (!hasSelectedVideoTrack) {
                        selectAndSeekVideoTrack();
                    }
                    break;
            }
        } else {
            source.selectTrack(type);
        }
    }

    private void selectAndSeekVideoTrack() {
        source.selectTrack(TrackType.VIDEO);
        source.requireExtractor().seekTo(trimStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        hasSelectedVideoTrack = true;
    }

    /**
     * Check if trim operation was completed successfully for selected track.
     * We apply the seek operation for the video track only, so all audio frames are skipped
     * until MediaExtractor reaches the first video key frame.
     * In the case there's no video track, audio frames are skipped until extractor reaches trimStartUs.
     */
    private boolean isTrackReady(@NonNull TrackType type) {
        final MediaExtractor extractor = source.requireExtractor();
        final long timestampUs = extractor.getSampleTime();
        if (type == TrackType.VIDEO) {
            isSeekTrackReady = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        } else if (type == TrackType.AUDIO && !hasVideoTrack) {
            isSeekTrackReady = timestampUs >= trimStartUs;
        }

        if (isSeekTrackReady) {
            trimDurationUs += trimStartUs - timestampUs;
            trimStartUs = timestampUs;
            LOG.v("First " + type + " key frame is at " + trimStartUs + ", actual duration will be " + trimDurationUs);
        } else {
            extractor.advance();
        }
        return isSeekTrackReady;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        boolean canRead = source.canReadTrack(type);

        if (canRead) {
            return isSeekTrackReady || isTrackReady(type);
        } else {
            return false;
        }
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        source.readTrack(chunk);
        chunk.timestampUs -= trimStartUs;
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
                hasSelectedVideoTrack = false;
                break;
            case VIDEO:
                isSeekTrackReady = false;
                break;
        }
        source.releaseTrack(type);
    }

    @Override
    public void rewind() {
        hasSelectedVideoTrack = false;
        isSeekTrackReady = false;
        source.rewind();
    }
}
