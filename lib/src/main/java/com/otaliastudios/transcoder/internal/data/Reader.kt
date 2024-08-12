package com.otaliastudios.transcoder.internal.data

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource
import java.nio.ByteBuffer


internal data class ReaderData(val chunk: DataSource.Chunk, val id: Int)

internal interface ReaderChannel : Channel {
    fun buffer(): Pair<ByteBuffer, Int>?
}

internal class Reader(
        private val source: DataSource,
        private val track: TrackType
) : BaseStep<Unit, Channel, ReaderData, ReaderChannel>() {

    private val log = Logger("Reader")
    override val channel = Channel
    private val chunk = DataSource.Chunk()

    private inline fun nextBufferOrWait(action: (ByteBuffer, Int) -> State<ReaderData>): State<ReaderData> {
        val buffer = next.buffer()
        if (buffer == null) {
            // dequeueInputBuffer failed
            log.v("Returning State.Wait because buffer is null.")
            return State.Wait(true)
        } else {
            return action(buffer.first, buffer.second)
        }
    }

    override fun step(state: State.Ok<Unit>, fresh: Boolean): State<ReaderData> {
        return if (source.isDrained) {
            log.i("Source is drained! Returning Eos as soon as possible.")
            nextBufferOrWait { byteBuffer, id ->
                byteBuffer.limit(0)
                chunk.buffer = byteBuffer
                chunk.keyframe = false
                chunk.render = true
                State.Eos(ReaderData(chunk, id))
            }
        } else if (!source.canReadTrack(track)) {
            log.i("Returning State.Wait because source can't read $track right now.")
            State.Wait(false)
        } else {
            nextBufferOrWait { byteBuffer, id ->
                chunk.buffer = byteBuffer
                source.readTrack(chunk)
                State.Ok(ReaderData(chunk, id))
            }
        }
    }
}
