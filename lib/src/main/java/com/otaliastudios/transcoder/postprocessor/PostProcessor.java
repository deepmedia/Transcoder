package com.otaliastudios.transcoder.postprocessor;

public interface PostProcessor {
    /**
     * Returns the duration of the data source on it has been processed (after calling the postProcess() method)
     * @param durationUs the original duratin in Us
     * @return the new duration in Us
     */
    long calculateNewDurationUs(long durationUs);
}
