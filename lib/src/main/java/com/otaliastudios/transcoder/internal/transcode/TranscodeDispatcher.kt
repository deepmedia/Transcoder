package com.otaliastudios.transcoder.internal.transcode

import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.TranscoderListener

/**
 * Wraps a TranscoderListener and posts events on the given handler.
 */
internal class TranscodeDispatcher(options: TranscoderOptions) {
    private val mHandler = options.listenerHandler
    private val mListener = options.listener
    fun dispatchCancel() {
        mHandler.post { mListener?.onTranscodeCanceled() }
    }

    fun dispatchSuccess(successCode: Int) {
        mHandler.post { mListener?.onTranscodeCompleted(successCode) }
    }

    fun dispatchFailure(exception: Throwable) {
        mHandler.post { mListener?.onTranscodeFailed(exception) }
    }

    fun dispatchProgress(progress: Double) {
        mHandler.post { mListener?.onTranscodeProgress(progress) }
    }

}
