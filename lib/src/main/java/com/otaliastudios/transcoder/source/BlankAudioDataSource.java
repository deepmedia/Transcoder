package com.otaliastudios.transcoder.source;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.common.TrackType;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.otaliastudios.transcoder.internal.audio.ConversionsKt.bitRate;
import static com.otaliastudios.transcoder.internal.audio.ConversionsKt.bytesToUs;
import static com.otaliastudios.transcoder.internal.audio.ConversionsKt.samplesToBytes;
import static com.otaliastudios.transcoder.internal.media.MediaFormatConstants.MIMETYPE_AUDIO_RAW;

/**
 * A {@link DataSource} that provides a silent audio track of the a specific duration.
 * This class can be used to concatenate a DataSources that has a video track only with another
 * that has both video and audio track.
 */
public class BlankAudioDataSource implements DataSource {

    private static final int CHANNEL_COUNT = 2;
    private static final int SAMPLE_RATE = 44100;
    // "period" = our MediaFormat.KEY_MAX_INPUT_SIZE
    private static final int PERIOD_SIZE = samplesToBytes(2048, CHANNEL_COUNT);

    private final long durationUs;

    private ByteBuffer byteBuffer;
    private MediaFormat audioFormat;
    private long positionUs = 0L;
    private boolean initialized = false;

    public BlankAudioDataSource(long durationUs) {
        this.durationUs = durationUs;
    }

    @Override
    public void initialize() {
        byteBuffer = ByteBuffer.allocateDirect(PERIOD_SIZE).order(ByteOrder.nativeOrder());
        audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, MIMETYPE_AUDIO_RAW);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate(SAMPLE_RATE, CHANNEL_COUNT));
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PERIOD_SIZE);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
        initialized = true;
    }

    @Override
    public void selectTrack(@NonNull TrackType type) {
        // Nothing to do
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        // Nothing to do
    }

    @Override
    public void deinitialize() {
        positionUs = 0;
        initialized = false;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
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


    @Override
    public long seekTo(long desiredPositionUs) {
        positionUs = desiredPositionUs;
        return desiredPositionUs;
    }

    @Nullable
    @Override
    public MediaFormat getTrackFormat(@NonNull TrackType type) {
        return (type == TrackType.AUDIO) ? audioFormat : null;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        return type == TrackType.AUDIO;
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        // Could fill the chunk with a while loop in case it's bigger than our buffer,
        // but let's respect our MediaFormat.KEY_MAX_INPUT_SIZE
        int position = chunk.buffer.position();
        int bytes = Math.min(chunk.buffer.remaining(), PERIOD_SIZE);

        byteBuffer.clear();
        byteBuffer.limit(bytes);
        chunk.buffer.put(byteBuffer);
        chunk.buffer.position(position);
        chunk.buffer.limit(position + bytes);

        chunk.keyframe = true;
        chunk.timeUs = positionUs;
        chunk.render = true;
        positionUs += bytesToUs(bytes, SAMPLE_RATE, CHANNEL_COUNT);
    }

    @Override
    public long getReadUs() {
        return positionUs;
    }

    @Override
    public boolean isDrained() {
        return positionUs >= getDurationUs();
    }
}
