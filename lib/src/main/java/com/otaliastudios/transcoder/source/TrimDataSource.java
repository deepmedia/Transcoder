package com.otaliastudios.transcoder.source;


import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;

import org.jetbrains.annotations.Contract;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

/**
 * A {@link DataSource} wrapper that trims source at both ends.
 */
public class TrimDataSource implements DataSource {
    private static final String TAG = "TrimDataSource";
    private static final Logger LOG = new Logger(TAG);
    private static final int UNKNOWN = -1;

    @NonNull
    private MediaExtractorDataSource source;
    private long trimStartUs;
    private long trimDurationUs;
    private boolean isVideoTrackReady = false;
    private boolean hasSelectedVideoTrack = false;

    public TrimDataSource(@NonNull MediaExtractorDataSource source, long trimStartMillis, long trimEndMillis) {
        this.source = source;
        this.trimStartUs = MILLISECONDS.toMicros(trimStartMillis);
        final long trimEndUs = MILLISECONDS.toMicros(trimEndMillis);
        this.trimDurationUs = computeTrimDuration(source.getDurationUs(), trimStartUs, trimEndUs);
    }

    @Contract(pure = true)
    private static long computeTrimDuration(long duration, long trimStart, long trimEnd) {
        if (duration == UNKNOWN) {
            return UNKNOWN;
        } else {
            final long result = duration - trimStart - trimEnd;
            return result >= 0 ? result : UNKNOWN;
        }
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
                    if (hasTrack(TrackType.VIDEO) && !hasSelectedVideoTrack) {
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

    private boolean hasTrack(@NonNull TrackType type) {
        return source.getTrackFormat(type) != null;
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
     */
    private boolean isTrackReady(@NonNull TrackType type) {
        if (isVideoTrackReady) {
            return true;
        }
        final MediaExtractor extractor = source.requireExtractor();
        if (type == TrackType.VIDEO) {
            final boolean isKeyFrame = (extractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
            if (isKeyFrame) {
                final long originalTrimStartUs = trimStartUs;
                trimStartUs = extractor.getSampleTime();
                trimDurationUs += originalTrimStartUs - trimStartUs;
                LOG.v("First video key frame is at " + trimStartUs + ", actual duration will be " + trimDurationUs);
                isVideoTrackReady = true;
                return true;
            }
        }
        extractor.advance();
        return false;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        boolean canRead = source.canReadTrack(type);

        if (canRead) {
            return isTrackReady(type);
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
                isVideoTrackReady = false;
                break;
        }
        source.releaseTrack(type);
    }

    @Override
    public void rewind() {
        hasSelectedVideoTrack = false;
        isVideoTrackReady = false;
        source.rewind();
    }
}
