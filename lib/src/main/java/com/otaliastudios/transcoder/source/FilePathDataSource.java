package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * A {@link DataSource} backed by a file absolute path.
 */
public class FilePathDataSource extends DefaultDataSource {
    private static final String TAG = FilePathDataSource.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    private final FileDescriptorDataSource descriptor;
    private FileInputStream stream;

    public FilePathDataSource(@NonNull String path) {
        FileDescriptor fileDescriptor;
        try {
            stream = new FileInputStream(path);
            fileDescriptor = stream.getFD();
        } catch (IOException e) {
            release();
            throw new RuntimeException(e);
        }
        descriptor = new FileDescriptorDataSource(fileDescriptor);
    }

    @Override
    public void applyExtractor(@NonNull MediaExtractor extractor) throws IOException {
        descriptor.applyExtractor(extractor);
    }

    @Override
    public void applyRetriever(@NonNull MediaMetadataRetriever retriever) {
        descriptor.applyRetriever(retriever);
    }

    @Override
    protected void release() {
        super.release();
        descriptor.release();
        if (stream != null) {
            try {
                stream.close();
            } catch (IOException e) {
                LOG.e("Can't close input stream: ", e);
            }
        }
    }
}
