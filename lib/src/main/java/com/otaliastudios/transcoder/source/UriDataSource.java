package com.otaliastudios.transcoder.source;

import android.content.Context;
import android.media.MediaExtractor;
import android.media.MediaMetadataRetriever;
import android.net.Uri;

import java.io.IOException;

import androidx.annotation.NonNull;

/**
 * A {@link DataSource} backed by an Uri, possibly
 * a content:// uri.
 */
public class UriDataSource implements DataSource {

    @NonNull private Context context;
    @NonNull private Uri uri;

    public UriDataSource(@NonNull Context context, @NonNull Uri uri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
    }

    @Override
    public void apply(@NonNull MediaExtractor extractor) throws IOException  {
        extractor.setDataSource(context, uri, null);
    }

    @Override
    public void apply(@NonNull MediaMetadataRetriever retriever) {
        retriever.setDataSource(context, uri);
    }

    @Override
    public void release() { }
}
