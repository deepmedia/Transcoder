package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;

import androidx.annotation.NonNull;

import java.io.IOException;

/**
 * Represents the source of input data.
 */
public interface DataSource {

    void apply(@NonNull MediaExtractor extractor) throws IOException;

    void apply(@NonNull MediaMetadataRetriever retriever);

    void release();
}
