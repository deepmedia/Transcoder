package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.Pipeline
import com.otaliastudios.transcoder.internal.pipeline.State

internal class Segment(
        val type: TrackType,
        val index: Int,
        private val pipeline: Pipeline,
) {

    // private val log = Logger("Segment($type,$index)")
    private var state: State<Unit>? = null

    fun advance(): Boolean {
        state = pipeline.execute()
        return state is State.Ok
    }

    fun canAdvance(): Boolean {
        // log.v("canAdvance(): state=$state")
        return state == null || state !is State.Eos
    }

    fun needsSleep(): Boolean {
        when(val s = state ?: return false) {
            is State.Ok -> return false
            is State.Failure -> return s.sleep
        }
    }

    fun release() {
        pipeline.release()
    }
}
