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
    private FileDescriptor descriptor;

    public FileDescriptorDataSource(@NonNull FileDescriptor descriptor) {
        this.descriptor = descriptor;
    }

    @Override
    public void applyExtractor(@NonNull MediaExtractor extractor) throws IOException  {
        extractor.setDataSource(descriptor);
    }

    @Override
    public void applyRetriever(@NonNull MediaMetadataRetriever retriever) {
        retriever.setDataSource(descriptor);
    }
}
