package com.otaliastudios.transcoder.source;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;

import java.nio.ByteBuffer;

/**
 * Represents the source of input data.
 */
public interface DataSource {

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
     * Returns true if we can read the given track at this point.
     * If true if returned, source should expect a {@link #readTrack(Chunk)} call.
     *
     * @param type track type
     * @return true if we can read this track now
     */
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
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
     * Returns the total number of microseconds that have been read until now.
     *
     * @return total read us
     */
    long getReadUs();

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

    /**
     * Represents a chunk of data.
     * Can be used to read input from {@link #readTrack(Chunk)}.
     */
    class Chunk {
        public ByteBuffer buffer;
        public boolean isKeyFrame;
        public long timestampUs;
        public int bytes;
    }
}
