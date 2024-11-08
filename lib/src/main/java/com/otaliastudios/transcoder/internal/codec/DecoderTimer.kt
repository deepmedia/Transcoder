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

    private var lastTimeUs: Long = Long.MIN_VALUE
    private var lastRawTimeUs: Long = Long.MIN_VALUE

    override fun advance(state: State.Ok<DecoderData>): State<DecoderData> {
        if (state is State.Eos) return state
        require(state.value !is DecoderTimerData) {
            "Can't apply DecoderTimer twice."
        }
        val rawTimeUs = state.value.timeUs
        val timeUs = interpolator.interpolate(track, rawTimeUs)
        val timeStretch = if (lastTimeUs == Long.MIN_VALUE) {
            1.0
        } else {
            // TODO to be exact, timeStretch should be computed by comparing the NEXT timestamps
            //  with this, instead of comparing this with the PREVIOUS
            val durationUs = timeUs - lastTimeUs
            val rawDurationUs = rawTimeUs - lastRawTimeUs
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