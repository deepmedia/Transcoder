package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.engine.TrackType;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Represents the source of input data.
 */
public interface DataSource {

    /**
     * Called before starting to set the status for the given
     * track. The source object can check if the track is transcoding
     * using {@link TrackStatus#isTranscoding()}.
     *
     * @param type track type
     * @param status status
     */
    void setTrackStatus(@NonNull TrackType type, @NonNull TrackStatus status);

    @Nullable
    MediaFormat getFormat(@NonNull TrackType type);

    int getOrientation();

    @Nullable
    double[] getLocation();

    long getDurationUs();

    boolean isDrained();

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    boolean canRead(@NonNull TrackType type);

    void read(@NonNull DataSource.Chunk chunk);

    void release();

    class Chunk {
        public ByteBuffer buffer;
        public boolean isKeyFrame;
        public long timestampUs;
        public int bytes;
    }
}
