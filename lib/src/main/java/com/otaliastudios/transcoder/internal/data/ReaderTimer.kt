package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.TransformStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.time.TimeInterpolator

internal class ReaderTimer(
    private val track: TrackType,
    private val interpolator: TimeInterpolator
) : TransformStep<ReaderData, ReaderChannel>("ReaderTimer") {
    override fun advance(state: State.Ok<ReaderData>): State<ReaderData> {
        if (state is State.Eos) return state
        state.value.chunk.timeUs = interpolator.interpolate(track, state.value.chunk.timeUs)
        return state
    }
}