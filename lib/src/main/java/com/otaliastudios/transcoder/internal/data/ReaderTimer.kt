package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.DataStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.time.TimeInterpolator

class ReaderTimer(
    private val track: TrackType,
    private val interpolator: TimeInterpolator
) : DataStep<ReaderData, ReaderChannel>() {
    override fun step(state: State.Ok<ReaderData>, fresh: Boolean): State<ReaderData> {
        if (state is State.Eos) return state
        state.value.chunk.timeUs = interpolator.interpolate(track, state.value.chunk.timeUs)
        return state
    }
}
