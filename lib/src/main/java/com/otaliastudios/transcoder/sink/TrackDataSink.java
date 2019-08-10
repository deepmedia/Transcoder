package com.otaliastudios.transcoder.sink;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Build;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackStatus;
import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.TrackTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;


/**
 * A {@link DataSink} implementation that writes one of the tracks to an output file.
 */
public class TrackDataSink implements DataSink {

    private final TrackType type;
    private final FileOutputStream stream;

    public TrackDataSink(@NonNull String outputFilePath, @NonNull TrackType type) {
        File file = new File(outputFilePath);
        this.type = type;
        try {
            this.stream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void setOrientation(int rotation) {
        // Not implemented
    }

    @Override
    public void setLocation(double latitude, double longitude) {
        // Not implemented
    }

    @Override
    public void setTrackStatus(@NonNull TrackType type, @NonNull TrackStatus status) {
        // Not implemented
    }

    @Override
    public void setTrackFormat(@NonNull TrackType type, @NonNull MediaFormat format) {
        // Not implemented
    }

    @Override
    public void writeTrack(@NonNull TrackType type, @NonNull ByteBuffer byteBuffer, @NonNull MediaCodec.BufferInfo bufferInfo) {
        if (type == this.type) {
            try {
                stream.write(byteBuffer.array(), byteBuffer.position(), byteBuffer.limit() - byteBuffer.position());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    public void stop() {
        try {
            stream.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void release() {
    }
}
