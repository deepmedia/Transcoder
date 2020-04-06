package com.otaliastudios.transcoder.source;

import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.postprocessor.DefaultAudioPostProcessor;
import com.otaliastudios.transcoder.postprocessor.PostProcessor;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static com.otaliastudios.transcoder.internal.MediaFormatConstants.MIMETYPE_AUDIO_RAW;

/**
 * A {@link DataSource} that provides a silent audio track of the a specific duration.
 * This class can be used to concatenate a DataSources that has a video track only with another
 * that has both video and audio track.
 */
public class BlankAudioDataSource implements DataSource {
    private final static String TAG = BlankAudioDataSource.class.getSimpleName();

    private static final int CHANNEL_COUNT = 2;
    private static final int SAMPLE_RATE = 44100;
    private static final int BITS_PER_SAMPLE = 16;
    private static final int BIT_RATE = CHANNEL_COUNT * SAMPLE_RATE * BITS_PER_SAMPLE;
    private static final double SAMPLES_PER_PERIOD = 2048;
    private static final double PERIOD_TIME_SECONDS = SAMPLES_PER_PERIOD / SAMPLE_RATE;
    private static final long PERIOD_TIME_US = (long) (1000000 * PERIOD_TIME_SECONDS);
    private static final int PERIOD_SIZE = (int) (PERIOD_TIME_SECONDS * BIT_RATE / 8);

    private final long durationUs;
    private final ByteBuffer byteBuffer;
    private final MediaFormat audioFormat;

    private long currentTimestampUs = 0L;

    public BlankAudioDataSource(long durationUs) {
        this.durationUs = durationUs;
        this.byteBuffer = ByteBuffer.allocateDirect(PERIOD_SIZE).order(ByteOrder.nativeOrder());
        this.audioFormat = new MediaFormat();
        audioFormat.setString(MediaFormat.KEY_MIME, MIMETYPE_AUDIO_RAW);
        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        audioFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, CHANNEL_COUNT);
        audioFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, PERIOD_SIZE);
        audioFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, SAMPLE_RATE);
    }

    private PostProcessor postProcessor = new DefaultAudioPostProcessor();

    @Override
    public void setPostProcessor(@NonNull PostProcessor postProcessor) {
        this.postProcessor = postProcessor;
    }

    @Override
    public PostProcessor getPostProcessor() {
        return postProcessor;
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
        currentTimestampUs = desiredTimestampUs;
        return desiredTimestampUs;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        return type == TrackType.AUDIO;
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        byteBuffer.clear();
        chunk.buffer = byteBuffer;
        chunk.isKeyFrame = true;
        chunk.timestampUs = currentTimestampUs;
        chunk.bytes = PERIOD_SIZE;

        currentTimestampUs += PERIOD_TIME_US;
    }

    @Override
    public long getReadUs() {
        return currentTimestampUs;
    }

    @Override
    public boolean isDrained() {
        return currentTimestampUs >= getDurationUs();
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        // Nothing to do
    }

    @Override
    public void rewind() {
        currentTimestampUs = 0;
    }
}
