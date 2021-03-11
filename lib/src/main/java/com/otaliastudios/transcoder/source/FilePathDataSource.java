package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;

import com.otaliastudios.transcoder.internal.Logger;

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
    private static final String TAG = FilePathDataSource.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);

    private FileDescriptorDataSource mDescriptorSource;
    private FileInputStream mStream;
    private final String mPath;

    public FilePathDataSource(@NonNull String path) {
        mPath = path;
    }

    private void ensureDescriptorSource() {
        if (mDescriptorSource == null) {
            try {
                mStream = new FileInputStream(mPath);
                mDescriptorSource = new FileDescriptorDataSource(mStream.getFD());
            } catch (IOException e) {
                release();
                throw new RuntimeException(e);
            }
        }
    }

    @Override
    protected void applyExtractor(@NonNull MediaExtractor extractor) throws IOException {
        ensureDescriptorSource();
        mDescriptorSource.applyExtractor(extractor);
    }

    @Override
    protected void applyRetriever(@NonNull MediaMetadataRetriever retriever) {
        ensureDescriptorSource();
        mDescriptorSource.applyRetriever(retriever);
    }

    @Override
    protected void release() {
        super.release();
        if (mDescriptorSource != null) {
            mDescriptorSource.release();
        }
        if (mStream != null) {
            try {
                mStream.close();
            } catch (IOException e) {
                LOG.e("Can't close input stream: ", e);
            }
        }
    }

    @Override
    public void rewind() {
        super.rewind();
        // I think we must recreate the stream to restart reading from the very first bytes.
        // This means that we must also recreate the underlying source.
        if (mDescriptorSource != null) {
            mDescriptorSource.release();
        }
        if (mStream != null) {
            try {
                mStream.close();
            } catch (IOException ignore) { }
        }
        mDescriptorSource = null;
        mStream = null;
    }
}
