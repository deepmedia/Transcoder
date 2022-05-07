package com.otaliastudios.transcoder.source;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.common.TrackType;

import java.nio.ByteBuffer;
import java.util.ArrayList;

/**
 * Represents the source of input data.
 */
public interface DataSource {

    void initialize();

    void deinitialize();

    boolean isInitialized();

    /**
     * Metadata information. Returns the video orientation, or 0.
     *
     * @return video metadata orientation
     */
    int getOrientation();

    /**
     * Metadata information. Returns the video location, or null.
     *
     * @return video location or null
     */
    @Nullable
    double[] getLocation();

    /**
     * Returns the video total duration in microseconds.
     *
     * @return duration in us
     */
    long getDurationUs();

    /**
     * Called before starting to inspect the input format for this track.
     * Can return null if this media does not include this track type.
     *
     * @param type track type
     * @return format or null
     */
    @Nullable
    MediaFormat getTrackFormat(@NonNull TrackType type);

    /**
     * Called before starting, but after {@link #getTrackFormat(TrackType)},
     * to select the given track.
     *
     * @param type track type
     */
    void selectTrack(@NonNull TrackType type);

    /**
     * Moves all selected tracks to the specified presentation time.
     * The timestamp should be between 0 and {@link #getDurationUs()}.
     * The actual timestamp might differ from the desired one because of
     * seeking constraints (e.g. seek to sync frames). It will typically be smaller
     * because we use {@link android.media.MediaExtractor#SEEK_TO_PREVIOUS_SYNC} in
     * the default source.
     *
     * @param desiredPositionUs requested timestamp
     * @return actual timestamp, likely smaller or equal
     */
    long seekTo(long desiredPositionUs);

    /**
     * Returns true if we can read the given track at this point.
     * If true if returned, source should expect a {@link #readTrack(Chunk)} call.
     *
     * @param type track type
     * @return true if we can read this track now
     */
    boolean canReadTrack(@NonNull TrackType type);

    /**
     * Called to read contents for the current track type.
     * Contents should be put inside {@link DataSource.Chunk#buffer}, and the
     * other chunk flags should be filled.
     *
     * @param chunk output chunk
     */
    void readTrack(@NonNull DataSource.Chunk chunk);

    /**
     * Returns the current read position, between 0 and duration.
     * @return position in us
     */
    long getPositionUs();

    /**
     * When this source has been totally read, it can return true here to
     * notify an end of input stream.
     *
     * @return true if drained
     */
    boolean isDrained();

    /**
     * Called to release resources for a given track.
     * @param type track type
     */
    void releaseTrack(@NonNull TrackType type);

    default long requestKeyFrameTimestamps() { return -1;}

    default ArrayList<Long> getKeyFrameTimestamps() {
        return new ArrayList<>();
    }

    default long getSeekThreshold() {
        return 0;
    }
    default String mediaId() { return "";}
    /**
     * Rewinds this source, moving it to its default state.
     * To be used again, tracks will be selected again.
     * After this call, for instance,
     * - {@link #getPositionUs()} should be 0
     * - {@link #isDrained()} should be false
     * - {@link #readTrack(Chunk)} should return the very first bytes
     */
    // void rewind();

    /**
     * Represents a chunk of data.
     * Can be used to read input from {@link #readTrack(Chunk)}.
     */
    class Chunk {
        public ByteBuffer buffer;
        public boolean keyframe;
        public long timeUs;
        public boolean render;
    }
    class KeyFrames {
        public ArrayList<Long> keyFrameTimestampListUs;
    }
}
