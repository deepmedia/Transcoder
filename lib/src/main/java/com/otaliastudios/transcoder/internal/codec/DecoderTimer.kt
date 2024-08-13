package com.otaliastudios.transcoder.internal.codec

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.TransformStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.time.TimeInterpolator
import java.nio.ByteBuffer

internal class DecoderTimerData(
        buffer: ByteBuffer,
        val rawTimeUs: Long,
        timeUs: Long,
        val timeStretch: Double,
        release: (render: Boolean) -> Unit
) : DecoderData(buffer, timeUs, release)

internal class DecoderTimer(
        private val track: TrackType,
        private val interpolator: TimeInterpolator,
) : TransformStep<DecoderData, DecoderChannel>("DecoderTimer") {

    private var lastTimeUs: Long? = null
    private var lastRawTimeUs: Long? = null

    override fun step(state: State.Ok<DecoderData>, fresh: Boolean): State<DecoderData> {
        if (state is State.Eos) return state
        require(state.value !is DecoderTimerData) {
            "Can't apply DecoderTimer twice."
        }
        val rawTimeUs = state.value.timeUs
        val timeUs = interpolator.interpolate(track, rawTimeUs)
        val timeStretch = if (lastTimeUs == null) {
            1.0
        } else {
            // TODO to be exact, timeStretch should be computed by comparing the NEXT timestamps
            //  with this, instead of comparing this with the PREVIOUS
            val durationUs = timeUs - lastTimeUs!!
            val rawDurationUs = rawTimeUs - lastRawTimeUs!!
            durationUs.toDouble() / rawDurationUs
        }
        lastTimeUs = timeUs
        lastRawTimeUs = rawTimeUs

        return State.Ok(DecoderTimerData(
                buffer = state.value.buffer,
                rawTimeUs = rawTimeUs,
                timeUs = timeUs,
                timeStretch = timeStretch,
                release = state.value.release
        ))
    }
}