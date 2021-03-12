package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.common.TrackType;
import com.otaliastudios.transcoder.internal.utils.ISO6709LocationParser;
import com.otaliastudios.transcoder.internal.utils.Logger;
import com.otaliastudios.transcoder.internal.utils.MutableTrackMap;
import com.otaliastudios.transcoder.internal.utils.TrackMap;

import java.io.IOException;
import java.util.HashSet;

import static com.otaliastudios.transcoder.internal.utils.TrackMapKt.mutableTrackMapOf;

/**
 * A DataSource implementation that uses Android's Media APIs.
 */
public abstract class DefaultDataSource implements DataSource {

    private final static Logger LOG = new Logger("DefaultDataSource");

    private MediaMetadataRetriever mMetadata = new MediaMetadataRetriever();
    private MediaExtractor mExtractor = new MediaExtractor();
    private boolean mMetadataApplied;
    private boolean mExtractorApplied;
    private final MutableTrackMap<MediaFormat> mFormat = mutableTrackMapOf(null);
    private final MutableTrackMap<Integer> mIndex = mutableTrackMapOf(null);
    private final HashSet<TrackType> mSelectedTracks = new HashSet<>();
    private final MutableTrackMap<Long> mLastTimestampUs = mutableTrackMapOf(0L, 0L);
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
        mExtractor.selectTrack(mIndex.get(type));
    }

    @Override
    public long seekTo(long desiredTimestampUs) {
        ensureExtractor();
        long base = mFirstTimestampUs > 0 ? mFirstTimestampUs : mExtractor.getSampleTime();
        boolean hasVideo = mSelectedTracks.contains(TrackType.VIDEO);
        boolean hasAudio = mSelectedTracks.contains(TrackType.AUDIO);
        LOG.i("Seeking to: " + ((base + desiredTimestampUs) / 1000) + " first: " + (base / 1000)
                + " hasVideo: " + hasVideo
                + " hasAudio: " + hasAudio);
        mExtractor.seekTo(base + desiredTimestampUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        if (hasVideo && hasAudio) {
            // Special case: audio can be moved to any timestamp, but video will only stop in
            // sync frames. MediaExtractor is not smart enough to sync the two tracks at the
            // video sync frame, so we must do it by seeking AGAIN at the next video position.
            while (mExtractor.getSampleTrackIndex() != mIndex.getVideo()) {
                mExtractor.advance();
            }
            LOG.i("Second seek to " + (mExtractor.getSampleTime() / 1000));
            mExtractor.seekTo(mExtractor.getSampleTime(), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        }
        return mExtractor.getSampleTime() - base;
    }

    @Override
    public boolean isDrained() {
        ensureExtractor();
        return mExtractor.getSampleTrackIndex() < 0;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        ensureExtractor();
        return mExtractor.getSampleTrackIndex() == mIndex.get(type);
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
        TrackType type = (mIndex.getHasAudio() && mIndex.getAudio() == index) ? TrackType.AUDIO
                : (mIndex.getHasVideo() && mIndex.getVideo() == index) ? TrackType.VIDEO
                : null;
        if (type == null) {
            throw new RuntimeException("Unknown type: " + index);
        }
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
        long last = Math.max(mLastTimestampUs.getAudio(), mLastTimestampUs.getVideo());
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
        if (mFormat.has(type)) return mFormat.get(type);
        ensureExtractor();
        int trackCount = mExtractor.getTrackCount();
        MediaFormat format;
        for (int i = 0; i < trackCount; i++) {
            format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (type == TrackType.VIDEO && mime.startsWith("video/")) {
                mIndex.setVideo(i);
                mFormat.setVideo(format);
                return format;
            }
            if (type == TrackType.AUDIO && mime.startsWith("audio/")) {
                mIndex.setAudio(i);
                mFormat.setAudio(format);
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
