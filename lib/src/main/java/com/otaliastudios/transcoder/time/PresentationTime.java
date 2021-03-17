package com.otaliastudios.transcoder.time;

public class PresentationTime {
    private long mTotalEncoderDurationUs = 0;
    public void increaseEncoderDuration(long encoderDurationUs) {
        mTotalEncoderDurationUs += encoderDurationUs;
    }
    public long getEncoderPresentationTimeUs() {
        return mTotalEncoderDurationUs;
    }
}
