package com.otaliastudios.transcoder.strategy.size;

/**
 * A special {@link Size} that knows about which dimension is width
 * and which is height.
 *
 * See comments in {@link Resizer}.
 */
public class ExactSize extends Size {

    private int mWidth;
    private int mHeight;

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
