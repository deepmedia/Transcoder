package com.otaliastudios.transcoder.internal

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.transcode.TrackTranscoder

internal class Segment(
        val type: TrackType,
        val index: Int,
        private val transcoder: TrackTranscoder,
        outputFormat: MediaFormat
) {
    init {
        transcoder.setUp(outputFormat)
    }

    fun advance(forceInputEos: Boolean): Boolean {
        return transcoder.transcode(forceInputEos)
    }

    fun canAdvance(): Boolean {
        return !transcoder.isFinished
    }

    fun release() {
        transcoder.tearDown()
    }
}