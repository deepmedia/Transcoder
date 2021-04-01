package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;

import com.otaliastudios.transcoder.internal.utils.Logger;

import java.io.FileInputStream;
import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * A {@link DataSource} backed by a file absolute path.
 *
 * This class actually wraps a {@link FileDescriptorDataSource} for the apply() operations.
 * We could pass the path directly to MediaExtractor and MediaMetadataRetriever, but that is
 * discouraged since they could not be able to open the file from another process.
 *
 * See {@link MediaExtractor#setDataSource(String)} documentation.
 */
public class FilePathDataSource extends DefaultDataSource {
    private static final Logger LOG = new Logger("FilePathDataSource");

    private FileDescriptorDataSource mDescriptorSource;
    private FileInputStream mStream;
    private final String mPath;

    public FilePathDataSource(@NonNull String path) {
        mPath = path;
    }

    @Override
    public void initialize() {
        try {
            mStream = new FileInputStream(mPath);
            mDescriptorSource = new FileDescriptorDataSource(mStream.getFD());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.initialize();
    }

    @Override
    public void deinitialize() {
        // I think we must recreate the stream to restart reading from the very first bytes.
        // This means that we must also recreate the underlying source.
        mDescriptorSource.deinitialize();
        try { mStream.close(); } catch (IOException ignore) { }
        super.deinitialize();
    }

    @Override
    protected void initializeExtractor(@NonNull MediaExtractor extractor) throws IOException {
        mDescriptorSource.initializeExtractor(extractor);
    }

    @Override
    protected void initializeRetriever(@NonNull MediaMetadataRetriever retriever) {
        mDescriptorSource.initializeRetriever(retriever);
    }
}
