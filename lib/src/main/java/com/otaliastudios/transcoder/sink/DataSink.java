package com.otaliastudios.transcoder.sink;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.transcode.TrackTranscoder;

import java.nio.ByteBuffer;

public interface DataSink {

    void setOrientation(int rotation);

    void setLocation(double latitude, double longitude);

    void setTrackStatus(@NonNull TrackType type,
                        @NonNull TrackStatus status);

    void setTrackOutputFormat(@NonNull TrackTranscoder transcoder,
                              @NonNull TrackType type,
                              @NonNull MediaFormat format);

    void write(@NonNull TrackTranscoder transcoder,
               @NonNull TrackType type,
               @NonNull ByteBuffer byteBuffer,
               @NonNull MediaCodec.BufferInfo bufferInfo);

    void stop();

    void release();
}
