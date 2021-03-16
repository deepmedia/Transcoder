package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;

import java.io.FileDescriptor;
import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * A {@link DataSource} backed by a file descriptor.
 */
public class FileDescriptorDataSource extends DefaultDataSource {

    @NonNull
    private final FileDescriptor descriptor;

    public FileDescriptorDataSource(@NonNull FileDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    protected void initializeExtractor(@NonNull MediaExtractor extractor) throws IOException  {
        extractor.setDataSource(descriptor);
    }

    @Override
    protected void initializeRetriever(@NonNull MediaMetadataRetriever retriever) {
        retriever.setDataSource(descriptor);
    }
}
