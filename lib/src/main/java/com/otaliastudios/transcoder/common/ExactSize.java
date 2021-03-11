package com.otaliastudios.transcoder.common;

import com.otaliastudios.transcoder.resize.Resizer;

/**
 * A special {@link Size} that knows about which dimension is width
 * and which is height.
 *
 * See comments in {@link Resizer}.
 */
public class ExactSize extends Size {

    private final int mWidth;
    private final int mHeight;

    public ExactSize(int width, int height) {
        super(width, height);
        mWidth = width;
        mHeight = height;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }
}
