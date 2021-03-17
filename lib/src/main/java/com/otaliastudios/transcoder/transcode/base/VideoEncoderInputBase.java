package com.otaliastudios.transcoder.transcode.base;

public interface VideoEncoderInputBase {
    void onFrame(long presentationTimeUs);
    void release();
}
