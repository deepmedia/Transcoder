package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource

class Seeker(
    private val source: DataSource,
    private val fetchPosition: () -> Long?,
    private val shouldSeek: (Long) -> Boolean
) : BaseStep<Unit, Channel, Unit, Channel>() {

    private val log = Logger("Seeker")
    override val channel = Channel

    override fun step(state: State.Ok<Unit>, fresh: Boolean): State<Unit> {
        val position = fetchPosition()
        if (position != null && shouldSeek(position)) {
            log.i("Seeking to next position $position")
            source.seekTo(position)
        }

        return state
    }
}
