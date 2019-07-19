package com.otaliastudios.transcoder;

import android.os.Handler;

import androidx.annotation.NonNull;

/**
 * Listeners for transcoder events. All the callbacks are called on the handler
 * specified with {@link TranscoderOptions.Builder#setListenerHandler(Handler)}.
 */
public interface TranscoderListener {
    /**
     * Called to notify progress.
     *
     * @param progress Progress in [0.0, 1.0] range, or negative value if progress is unknown.
     */
    void onTranscodeProgress(double progress);

    /**
     * Called when transcode completed. The success code can be either
     * {@link Transcoder#SUCCESS_TRANSCODED} or {@link Transcoder#SUCCESS_NOT_NEEDED}.
     *
     * @param successCode the success code
     */
    void onTranscodeCompleted(int successCode);

    /**
     * Called when transcode canceled.
     */
    void onTranscodeCanceled();

    /**
     * Called when transcode failed.
     * @param exception the failure exception
     */
    void onTranscodeFailed(@NonNull Throwable exception);
}
