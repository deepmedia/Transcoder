package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource

class Seeker(
    private val source: DataSource,
    private val shouldSeek: () -> Pair<Long, Boolean>
) : BaseStep<Unit, Channel, Unit, Channel>() {

    private val log = Logger("Seeker")
    override val channel = Channel

    override fun step(state: State.Ok<Unit>, fresh: Boolean): State<Unit> {
        val shouldSeek = shouldSeek()
        val position = shouldSeek.first
        if (shouldSeek.second) {
            log.i("Seeking to next position $position where currentReaderTime=${source.getPositionUs()}")
            source.seekTo(position)
        } else {
            log.i("Skipping seek for $position where currentReaderTime=${source.getPositionUs()}")
        }
        return state
    }
}
