package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.ISO6709LocationParser;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.TrackTypeMap;

import java.io.IOException;
import java.util.HashSet;

/**
 * A DataSource implementation that uses Android's Media APIs.
 */
public abstract class DefaultDataSource implements DataSource {

    private final static String TAG = DefaultDataSource.class.getSimpleName();
    private final static Logger LOG = new Logger(TAG);

    private MediaMetadataRetriever mMetadata = new MediaMetadataRetriever();
    private MediaExtractor mExtractor = new MediaExtractor();
    private boolean mMetadataApplied;
    private boolean mExtractorApplied;
    private final TrackTypeMap<MediaFormat> mFormats = new TrackTypeMap<>();
    private final TrackTypeMap<Integer> mIndex = new TrackTypeMap<>();
    private final HashSet<TrackType> mSelectedTracks = new HashSet<>();
    private final TrackTypeMap<Long> mLastTimestampUs
            = new TrackTypeMap<>(0L, 0L);
    private long mFirstTimestampUs = Long.MIN_VALUE;

    private void ensureMetadata() {
        if (!mMetadataApplied) {
            mMetadataApplied = true;
            applyRetriever(mMetadata);
        }
    }

    private void ensureExtractor() {
        if (!mExtractorApplied) {
            mExtractorApplied = true;
            try {
                applyExtractor(mExtractor);
            } catch (IOException e) {
                LOG.e("Got IOException while trying to open MediaExtractor.", e);
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract void applyExtractor(@NonNull MediaExtractor extractor) throws IOException;

    protected abstract void applyRetriever(@NonNull MediaMetadataRetriever retriever);

    @Override
    public void selectTrack(@NonNull TrackType type) {
        mSelectedTracks.add(type);
        mExtractor.selectTrack(mIndex.require(type));
    }

    @Override
    public void seekTo(long timestampUs) {
        ensureExtractor();
        long first = mFirstTimestampUs > 0 ? mFirstTimestampUs : mExtractor.getSampleTime();
        Log.w("TRIMBUG", "seeking to: " + ((first + timestampUs) / 1000) + " first: " + (first / 1000)
                + " hasVideo: " + mSelectedTracks.contains(TrackType.VIDEO)
                + " hasAudio: " + mSelectedTracks.contains(TrackType.AUDIO));
        mExtractor.seekTo(first + timestampUs, MediaExtractor.SEEK_TO_NEXT_SYNC);
    }

    @Override
    public boolean isDrained() {
        ensureExtractor();
        return mExtractor.getSampleTrackIndex() < 0;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        ensureExtractor();
        return mExtractor.getSampleTrackIndex() == mIndex.require(type);
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        ensureExtractor();
        int index = mExtractor.getSampleTrackIndex();
        chunk.bytes = mExtractor.readSampleData(chunk.buffer, 0);
        chunk.isKeyFrame = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        chunk.timestampUs = mExtractor.getSampleTime();
        if (mFirstTimestampUs == Long.MIN_VALUE) {
            mFirstTimestampUs = chunk.timestampUs;
        }
        TrackType type = (mIndex.hasAudio() && mIndex.requireAudio() == index) ? TrackType.AUDIO
                : (mIndex.hasVideo() && mIndex.requireVideo() == index) ? TrackType.VIDEO
                : null;
        if (type == null) {
            throw new RuntimeException("Unknown type: " + index);
        }
        Log.d("TRIMBUG", "type: " + type + " timestamp: " + (chunk.timestampUs / 1000));
        mLastTimestampUs.set(type, chunk.timestampUs);
        mExtractor.advance();
    }

    @Override
    public long getReadUs() {
        if (mFirstTimestampUs == Long.MIN_VALUE) {
            return 0;
        }
        // Return the fastest track.
        // This ensures linear behavior over time: if a track is behind the other,
        // this will not push down the readUs value, which might break some components
        // down the pipeline which expect a monotonically growing timestamp.
        long last = Math.max(mLastTimestampUs.requireAudio(), mLastTimestampUs.requireVideo());
        return last - mFirstTimestampUs;
    }

    @Nullable
    @Override
    public double[] getLocation() {
        ensureMetadata();
        String string = mMetadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        if (string != null) {
            float[] location = new ISO6709LocationParser().parse(string);
            if (location != null) {
                double[] result = new double[2];
                result[0] = (double) location[0];
                result[1] = (double) location[1];
                return result;
            }
        }
        return null;
    }

    @Override
    public int getOrientation() {
        ensureMetadata();
        String string = mMetadata.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        try {
            return Integer.parseInt(string);
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    @Override
    public long getDurationUs() {
        ensureMetadata();
        try {
            return Long.parseLong(mMetadata
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) * 1000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nullable
    @Override
    public MediaFormat getTrackFormat(@NonNull TrackType type) {
        if (mFormats.has(type)) return mFormats.get(type);
        ensureExtractor();
        int trackCount = mExtractor.getTrackCount();
        MediaFormat format;
        for (int i = 0; i < trackCount; i++) {
            format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (type == TrackType.VIDEO && mime.startsWith("video/")) {
                mIndex.set(TrackType.VIDEO, i);
                mFormats.set(TrackType.VIDEO, format);
                return format;
            }
            if (type == TrackType.AUDIO && mime.startsWith("audio/")) {
                mIndex.set(TrackType.AUDIO, i);
                mFormats.set(TrackType.AUDIO, format);
                return format;
            }
        }
        return null;
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        mSelectedTracks.remove(type);
        if (mSelectedTracks.isEmpty()) {
            release();
        }
    }

    protected void release() {
        try {
            mExtractor.release();
        } catch (Exception e) {
            LOG.w("Could not release extractor:", e);
        }
        try {
            mMetadata.release();
        } catch (Exception e) {
            LOG.w("Could not release metadata:", e);
        }
    }

    @Override
    public void rewind() {
        mSelectedTracks.clear();
        mFirstTimestampUs = Long.MIN_VALUE;
        mLastTimestampUs.setAudio(0L);
        mLastTimestampUs.setVideo(0L);
        // Release the extractor and recreate.
        try {
            mExtractor.release();
        } catch (Exception ignore) { }
        mExtractor = new MediaExtractor();
        mExtractorApplied = false;
        // Release the metadata and recreate.
        // This is not strictly needed but some subclasses could have
        // to close the underlying resource during rewind() and this could
        // make the metadata unusable as well.
        try {
            mMetadata.release();
        } catch (Exception ignore) { }
        mMetadata = new MediaMetadataRetriever();
        mMetadataApplied = false;
    }
}
