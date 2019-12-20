package com.otaliastudios.transcoder.source;

import android.media.MediaExtractor;

/**
 * DataSource that allows access to its MediaExtractor.
 */
abstract class MediaExtractorDataSource implements DataSource {
    abstract protected MediaExtractor requireExtractor();
}
