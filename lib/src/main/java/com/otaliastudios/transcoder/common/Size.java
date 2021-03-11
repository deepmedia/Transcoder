package com.otaliastudios.transcoder.common;

/**
 * Represents a video size in pixels,
 * with no notion of rotation / width / height.
 * This is just a minor dim and a major dim.
 */
public class Size {

    private final int mMajor;
    private final int mMinor;

    /**
     * The order does not matter.
     * @param firstSize one dimension
     * @param secondSize the other
     */
    @SuppressWarnings("WeakerAccess")
    public Size(int firstSize, int secondSize) {
        mMajor = Math.max(firstSize, secondSize);
        mMinor = Math.min(firstSize, secondSize);
    }

    public int getMinor() {
        return mMinor;
    }

    public int getMajor() {
        return mMajor;
    }
}
