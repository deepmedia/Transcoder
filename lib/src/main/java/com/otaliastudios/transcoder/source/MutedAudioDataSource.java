package com.otaliastudios.transcoder.source;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.otaliastudios.transcoder.internal.MediaFormatConstants.MIMETYPE_AUDIO_RAW;

/**
 * A {@link DataSource} that provides a silent audio track of the a specific duration.
 * This class can be used to concatenate a DataSources that has a video track only with another
 * that has both video and audio track.
 */
public class MutedAudioDataSource implements DataSource {
    private final static String TAG = MutedAudioDataSource.class.getSimpleName();

    private static final int CHANNEL_COUNT = 2;
    private static final int BIT_RATE = 64000;
    private final static int SAMPLE_RATE = 48000;
    private final static int BUFFER_SIZE = 65536; // Lower values are ignored
    private final static int PERIOD_SIZE = BUFFER_SIZE * CHANNEL_COUNT / 32;
    private final static long PERIOD_TIME = 21333;

    private final MediaFormat audioFormat;
    private final long durationUs;

    private long mCurrentTimestampUs = 0L;

    public MutedAudioDataSource(Long durationUs) {
        this.durationUs = durationUs;
        this.audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, MIMETYPE_AUDIO_RAW);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, BUFFER_SIZE);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
    }

    @Override
    public int getOrientation() {
        return 0;
    }

    @Nullable
    @Override
    public double[] getLocation() {
        return null;
    }

    @Override
    public long getDurationUs() {
        return durationUs;
    }

    @Nullable
    @Override
    public MediaFormat getTrackFormat(@NonNull TrackType type) {
        return (type == TrackType.AUDIO) ? audioFormat : null;
    }

    @Override
    public void selectTrack(@NonNull TrackType type) {
        // Nothing to do
    }

    @Override
    public long seekTo(long desiredTimestampUs) {
        return (desiredTimestampUs <= durationUs) ? desiredTimestampUs : -1;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        return (type == TrackType.AUDIO) && !isDrained();
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        chunk.buffer = ByteBuffer.allocateDirect(BUFFER_SIZE).order(ByteOrder.nativeOrder());
        chunk.isKeyFrame = true;
        if (isDrained()) {
            chunk.timestampUs = 0;
            chunk.bytes = -1;
        } else {
            chunk.timestampUs = mCurrentTimestampUs;
            chunk.bytes = PERIOD_SIZE;
        }

        mCurrentTimestampUs += PERIOD_TIME;
    }

    @Override
    public long getReadUs() {
        return mCurrentTimestampUs;
    }

    @Override
    public boolean isDrained() {
        return mCurrentTimestampUs >= getDurationUs();
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        // Nothing to do
    }

    @Override
    public void rewind() {
        // Nothing to do
    }
}
