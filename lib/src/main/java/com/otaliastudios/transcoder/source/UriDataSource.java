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
public class UriDataSource extends DefaultDataSource {

    @NonNull private final Context context;
    @NonNull private final Uri uri;

    public UriDataSource(@NonNull Context context, @NonNull Uri uri) {
        this.context = context.getApplicationContext();
        this.uri = uri;
    }

    @Override
    protected void applyExtractor(@NonNull MediaExtractor extractor) throws IOException  {
        extractor.setDataSource(context, uri, null);
    }

    @Override
    protected void applyRetriever(@NonNull MediaMetadataRetriever retriever) {
        retriever.setDataSource(context, uri);
    }
}
