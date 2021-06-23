package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource

class Seeker(
        private val source: DataSource,
        private val fetchPositions:()-> List<Long>,
        private val seek: (Long) -> Boolean
) : BaseStep<Unit, Channel, Unit, Channel>() {

    private val log = Logger("Seeker")
    override val channel = Channel

    private val positions:MutableList<Long> = mutableListOf()

    override fun step(state: State.Ok<Unit>, fresh: Boolean): State<Unit> {
        if(positions.isEmpty()) {
            positions.addAll(fetchPositions())
            if (positions.isEmpty()) {
                return State.Eos(Unit)
            }
        }

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