package com.otaliastudios.transcoder.engine;

/**
 * Represents the status of a given track inside the transcoding operation.
 */
public enum TrackStatus {
    /**
     * Track was absent in the source.
     */
    ABSENT,

    /**
     * We are removing the track in the output.
     */
    REMOVING,

    /**
     * We are not touching the track.
     */
    PASS_THROUGH,

    /**
     * We are compressing the track in the output.
     */
    COMPRESSING;

    /**
     * This is used to understand whether we should select this track
     * in MediaExtractor, and add this track to MediaMuxer.
     * Basically if it should be read and written or not
     * (no point in just reading without writing).
     *
     * @return true if transcoding
     */
    public boolean isTranscoding() {
        switch (this) {
            case PASS_THROUGH: return true;
            case COMPRESSING: return true;
            case REMOVING: return false;
            case ABSENT: return false;
        }
        throw new RuntimeException("Unexpected track status: " + this);
    }
}
