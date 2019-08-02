package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.engine.TrackTypeMap;
import com.otaliastudios.transcoder.engine.internal.ISO6709LocationParser;
import com.otaliastudios.transcoder.internal.Logger;

import java.io.IOException;

/**
 * A DataSource implementation that uses Android's Media APIs.
 */
public abstract class AndroidDataSource implements DataSource {

    private final static String TAG = AndroidDataSource.class.getSimpleName();
    private final static Logger LOG = new Logger(TAG);

    private final MediaMetadataRetriever mMetadata = new MediaMetadataRetriever();
    private final MediaExtractor mExtractor = new MediaExtractor();
    private boolean mMetadataApplied;
    private boolean mExtractorApplied;
    private final TrackTypeMap<MediaFormat> mFormats = new TrackTypeMap<>();
    private final TrackTypeMap<Integer> mIndex = new TrackTypeMap<>();

    @SuppressWarnings("WeakerAccess")
    protected AndroidDataSource() { }

    private void ensureMetadata() {
        if (!mMetadataApplied) {
            mMetadataApplied = true;
            apply(mMetadata);
        }
    }

    private void ensureExtractor() {
        if (!mExtractorApplied) {
            mExtractorApplied = true;
            try {
                apply(mExtractor);
            } catch (IOException e) {
                LOG.e("Got IOException while trying to open MediaExtractor.", e);
                throw new RuntimeException(e);
            }
        }
    }

    protected abstract void apply(@NonNull MediaExtractor extractor) throws IOException;

    protected abstract void apply(@NonNull MediaMetadataRetriever retriever);

    @Override
    public void setTrackStatus(@NonNull TrackType type, @NonNull TrackStatus status) {
        if (status.isTranscoding()) {
            mExtractor.selectTrack(mIndex.require(type));
        }
    }

    @Override
    public boolean isDrained() {
        ensureExtractor();
        return mExtractor.getSampleTrackIndex() < 0;
    }

    @Override
    public boolean canRead(@NonNull TrackType type) {
        ensureExtractor();
        return mExtractor.getSampleTrackIndex() == mIndex.require(type);
    }

    @Override
    public void read(@NonNull Chunk chunk) {
        ensureExtractor();
        chunk.bytes = mExtractor.readSampleData(chunk.buffer, 0);
        chunk.isKeyFrame = (mExtractor.getSampleFlags() & MediaExtractor.SAMPLE_FLAG_SYNC) != 0;
        chunk.timestampUs = mExtractor.getSampleTime();
        mExtractor.advance();
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
    public MediaFormat getFormat(@NonNull TrackType type) {
        if (mFormats.has(type)) return mFormats.get(type);
        ensureExtractor();
        int trackCount = mExtractor.getTrackCount();
        MediaFormat format = null;
        for (int i = 0; i < trackCount; i++) {
            format = mExtractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (type == TrackType.VIDEO && mime.startsWith("video/")) {
                mIndex.set(TrackType.VIDEO, i);
                break;
            }
            if (type == TrackType.AUDIO && mime.startsWith("audio/")) {
                mIndex.set(TrackType.AUDIO, i);
                break;
            }
        }
        mFormats.set(type, format);
        return format;
    }

    @Override
    public void release() {
        try {
            mExtractor.release();
        } catch (Exception e) {
            LOG.w("Could not release extractor:", e);
        }
    }
}
