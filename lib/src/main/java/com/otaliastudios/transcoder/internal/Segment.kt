package com.otaliastudios.transcoder.internal

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.Pipeline
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.transcode.TrackTranscoder

internal class Segment(
        val type: TrackType,
        val index: Int,
        private val pipeline: Pipeline,
) {

    private var state: State<Unit>? = null

    fun advance(): Boolean {
        state = pipeline.execute()
        return state is State.Ok
    }

    fun canAdvance(): Boolean {
        return state == null || state !is State.Eos
    }

    fun release() {
        pipeline.release()
    }
}