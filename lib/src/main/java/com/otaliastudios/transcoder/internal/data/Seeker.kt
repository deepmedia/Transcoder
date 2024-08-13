package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource
import java.nio.ByteBuffer

internal class Seeker(
    private val source: DataSource,
    positions: List<Long>,
    private val seek: (Long) -> Boolean
) : BaseStep<Unit, Channel, Unit, Channel>("Seeker") {

    override val channel = Channel
    private val positions = positions.toMutableList()

    override fun advance(state: State.Ok<Unit>): State<Unit> {
        if (positions.isNotEmpty()) {
            if (seek(positions.first())) {
                log.i("Seeking to next position ${positions.first()}")
                val next = positions.removeFirst()
                source.seekTo(next)
            } else {
                // log.v("Not seeking to next Request. head=${positions.first()}")
            }
        }
        return state
    }
}