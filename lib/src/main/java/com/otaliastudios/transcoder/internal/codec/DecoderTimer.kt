package com.otaliastudios.transcoder.internal.codec

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.DataStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.time.TimeInterpolator

internal class DecoderTimer<Channel : com.otaliastudios.transcoder.internal.pipeline.Channel>(
        private val track: TrackType,
        private val interpolator: TimeInterpolator,
) : DataStep<DecoderData, DecoderData, Channel>() {

    override fun step(state: State.Ok<DecoderData>, fresh: Boolean): State<DecoderData> {
        if (state is State.Eos) return state
        val timeUs = interpolator.interpolate(track, state.value.timeUs)
        return State.Ok(state.value.copy(timeUs = timeUs))
    }
}