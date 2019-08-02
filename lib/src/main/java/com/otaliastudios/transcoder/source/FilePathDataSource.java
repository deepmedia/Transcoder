package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;

import com.otaliastudios.transcoder.internal.Logger;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A {@link DataSource} backed by a file absolute path.
 */
public class FilePathDataSource extends AndroidDataSource {
    private static final String TAG = "FilePathDataSource";
    private static final Logger LOG = new Logger(TAG);

    @NonNull
    private FileDescriptorDataSource descriptor;
    @Nullable private FileInputStream stream;

    public FilePathDataSource(@NonNull String path) {
        super();
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
    public void apply(@NonNull MediaExtractor extractor) throws IOException {
        descriptor.apply(extractor);
    }

    @Override
    public void apply(@NonNull MediaMetadataRetriever retriever) {
        descriptor.apply(retriever);
    }

    @Override
    public void release() {
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
