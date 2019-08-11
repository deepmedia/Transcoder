package com.otaliastudios.transcoder.sink;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.engine.TrackType;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class MultiDataSink implements DataSink {

    private final List<DataSink> sinks;

    public MultiDataSink(@NonNull DataSink... sink) {
        sinks = Arrays.asList(sink);
    }

    @Override
    public void setOrientation(int orientation) {
        for (DataSink sink : sinks) {
            sink.setOrientation(orientation);
        }
    }

    @Override
    public void setLocation(double latitude, double longitude) {
        for (DataSink sink : sinks) {
            sink.setLocation(latitude, longitude);
        }
    }

    @Override
    public void setTrackStatus(@NonNull TrackType type, @NonNull TrackStatus status) {
        for (DataSink sink : sinks) {
            sink.setTrackStatus(type, status);
        }
    }

    @Override
    public void setTrackFormat(@NonNull TrackType type, @NonNull MediaFormat format) {
        for (DataSink sink : sinks) {
            sink.setTrackFormat(type, format);
        }
    }

    @Override
    public void writeTrack(@NonNull TrackType type, @NonNull ByteBuffer byteBuffer, @NonNull MediaCodec.BufferInfo bufferInfo) {
        int position = byteBuffer.position();
        int limit = byteBuffer.limit();
        for (DataSink sink : sinks) {
            sink.writeTrack(type, byteBuffer, bufferInfo);
            byteBuffer.position(position);
            byteBuffer.limit(limit);
        }
    }

    @Override
    public void stop() {
        for (DataSink sink : sinks) {
            sink.stop();
        }
    }

    @Override
    public void release() {
        for (DataSink sink : sinks) {
            sink.release();
        }
    }
}
