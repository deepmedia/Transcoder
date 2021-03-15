package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.DataStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.time.TimeInterpolator

internal class ReaderTimer<Channel : com.otaliastudios.transcoder.internal.pipeline.Channel>(
        private val track: TrackType,
        private val interpolator: TimeInterpolator,
) : DataStep<ReaderData, ReaderData, Channel>() {

    override fun step(state: State.Ok<ReaderData>): State<ReaderData> {
        if (state is State.Eos) return state
        state.value.chunk.timestampUs = interpolator.interpolate(track, state.value.chunk.timestampUs)
        return state
    }
}