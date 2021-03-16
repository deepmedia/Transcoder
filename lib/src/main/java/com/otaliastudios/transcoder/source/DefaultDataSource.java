package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.common.TrackType;
import com.otaliastudios.transcoder.common.TrackTypeKt;
import com.otaliastudios.transcoder.internal.utils.ISO6709LocationParser;
import com.otaliastudios.transcoder.internal.utils.Logger;
import com.otaliastudios.transcoder.internal.utils.MutableTrackMap;

import java.io.IOException;
import java.util.HashSet;

import static android.media.MediaMetadataRetriever.METADATA_KEY_DURATION;
import static android.media.MediaMetadataRetriever.METADATA_KEY_LOCATION;
import static android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION;
import static com.otaliastudios.transcoder.internal.utils.DebugKt.stackTrace;
import static com.otaliastudios.transcoder.internal.utils.TrackMapKt.mutableTrackMapOf;

/**
 * A DataSource implementation that uses Android's Media APIs.
 */
public abstract class DefaultDataSource implements DataSource {

    private final Logger LOG = new Logger("DefaultDataSource(" + this.hashCode() + ")");

    private final MutableTrackMap<MediaFormat> mFormat = mutableTrackMapOf(null);
    private final MutableTrackMap<Integer> mIndex = mutableTrackMapOf(null);
    private final HashSet<TrackType> mSelectedTracks = new HashSet<>();
    private final MutableTrackMap<Long> mLastTimestampUs = mutableTrackMapOf(0L, 0L);

    private MediaMetadataRetriever mMetadata = null;
    private MediaExtractor mExtractor = null;
    private long mFirstTimestampUs = Long.MIN_VALUE;
    private boolean mInitialized = false;

    private long mDontRenderRangeStart = -1L;
    private long mDontRenderRangeEnd = -1L;

    @Override
    public void initialize() {
        mExtractor = new MediaExtractor();
        try {
            initializeExtractor(mExtractor);
        } catch (IOException e) {
            LOG.e("Got IOException while trying to open MediaExtractor.", e);
            throw new RuntimeException(e);
        }
        mMetadata = new MediaMetadataRetriever();
        initializeRetriever(mMetadata);

        int trackCount = mExtractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = mExtractor.getTrackFormat(i);
            TrackType type = TrackTypeKt.getTrackTypeOrNull(format);
            if (type != null && !mIndex.has(type)) {
                mIndex.set(type, i);
                mFormat.set(type, format);
            }
        }
        mInitialized = true;
    }

    @Override
    public void deinitialize() {
        LOG.i("release(): releasing..." + stackTrace());
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

        mSelectedTracks.clear();
        mFirstTimestampUs = Long.MIN_VALUE;
        mLastTimestampUs.reset(0L, 0L);
        mFormat.reset(null, null);
        mIndex.reset(null, null);
        mDontRenderRangeStart = -1;
        mDontRenderRangeEnd = -1;
        mInitialized = false;
    }

    @Override
    public boolean isInitialized() {
        return mInitialized;
    }

    @Override
    public void selectTrack(@NonNull TrackType type) {
        LOG.i("selectTrack(" + type + ")");
        mSelectedTracks.add(type);
        mExtractor.selectTrack(mIndex.get(type));
    }

    @Override
    public void releaseTrack(@NonNull TrackType type) {
        LOG.i("releaseTrack(" + type + ")");
        mSelectedTracks.remove(type);
        mExtractor.unselectTrack(mIndex.get(type));
    }

    protected abstract void initializeExtractor(@NonNull MediaExtractor extractor) throws IOException;

    protected abstract void initializeRetriever(@NonNull MediaMetadataRetriever retriever);

    @Override
    public long seekTo(long desiredPositionUs) {
        LOG.i("seekTo(" + desiredPositionUs + ")");
        long originUs = mFirstTimestampUs > 0 ? mFirstTimestampUs : mExtractor.getSampleTime();
        boolean hasVideo = mSelectedTracks.contains(TrackType.VIDEO);
        boolean hasAudio = mSelectedTracks.contains(TrackType.AUDIO);
        LOG.i("Seeking to: " + ((originUs + desiredPositionUs) / 1000)
                + " origin: " + (originUs / 1000)
                + " hasVideo: " + hasVideo
                + " hasAudio: " + hasAudio);
        if (hasVideo && hasAudio) {
            // Special case: audio can be moved to any timestamp, but video will only stop in
            // sync frames. MediaExtractor is not smart enough to sync the two tracks at the
            // video sync frame, so we must take care of this with the following trick.
            mExtractor.unselectTrack(mIndex.getAudio());
            mExtractor.seekTo(originUs + desiredPositionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
            mExtractor.selectTrack(mIndex.getAudio()); // second seek might not be needed, but should not hurt.
            mExtractor.seekTo(mExtractor.getSampleTime(), MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        } else {
            mExtractor.seekTo(originUs + desiredPositionUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        }
        mDontRenderRangeStart = mExtractor.getSampleTime();
        mDontRenderRangeEnd = originUs + desiredPositionUs;
        if (mDontRenderRangeStart > mDontRenderRangeEnd) {
            throw new IllegalStateException("The dontRenderRange has unexpected values! " +
                    "start=" + mDontRenderRangeStart + ", " +
                    "end=" + mDontRenderRangeEnd);
        } else {
            LOG.i("seekTo(): dontRenderRange=" +
                    mDontRenderRangeStart + ".." +
                    mDontRenderRangeEnd + " (" +
                    (mDontRenderRangeEnd - mDontRenderRangeStart) + "us)");
        }
        return mExtractor.getSampleTime() - originUs;
    }

    @Override
    public boolean isDrained() {
        return mExtractor.getSampleTrackIndex() < 0;
    }

    @Override
    public boolean canReadTrack(@NonNull TrackType type) {
        return mExtractor.getSampleTrackIndex() == mIndex.get(type);
    }

    @Override
    public void readTrack(@NonNull Chunk chunk) {
        int index = mExtractor.getSampleTrackIndex();
        chunk.bytes = mExtractor.readSampleData(chunk.buffer, 0);
        chunk.keyframe = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        chunk.timeUs = mExtractor.getSampleTime();
        chunk.render = chunk.timeUs < mDontRenderRangeStart || chunk.timeUs >= mDontRenderRangeEnd;
        if (mFirstTimestampUs == Long.MIN_VALUE) {
            mFirstTimestampUs = chunk.timeUs;
        }
        TrackType type = (mIndex.getHasAudio() && mIndex.getAudio() == index) ? TrackType.AUDIO
                : (mIndex.getHasVideo() && mIndex.getVideo() == index) ? TrackType.VIDEO
                : null;
        if (type == null) {
            throw new RuntimeException("Unknown type: " + index);
        }
        mLastTimestampUs.set(type, chunk.timeUs);
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
        LOG.i("getLocation()");
        String string = mMetadata.extractMetadata(METADATA_KEY_LOCATION);
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
        LOG.i("getOrientation()");
        try {
            return Integer.parseInt(mMetadata.extractMetadata(METADATA_KEY_VIDEO_ROTATION));
        } catch (NumberFormatException ignore) {
            return 0;
        }
    }

    @Override
    public long getDurationUs() {
        LOG.i("getDurationUs()");
        try {
            return Long.parseLong(mMetadata.extractMetadata(METADATA_KEY_DURATION)) * 1000;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Nullable
    @Override
    public MediaFormat getTrackFormat(@NonNull TrackType type) {
        LOG.i("getTrackFormat(" + type + ")");
        return mFormat.getOrNull(type);
    }
}
