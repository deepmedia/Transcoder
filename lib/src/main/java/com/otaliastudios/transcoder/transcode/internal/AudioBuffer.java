package com.otaliastudios.transcoder.transcode.internal;

import java.nio.ShortBuffer;

class AudioBuffer {
    int decoderBufferIndex = -1;
    long decoderTimestampUs = 0;
    ShortBuffer decoderData = null;
    boolean isEndOfStream = false;
}
