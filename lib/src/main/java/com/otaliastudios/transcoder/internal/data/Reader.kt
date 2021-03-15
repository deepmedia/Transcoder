package com.otaliastudios.transcoder.internal.data

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step
import com.otaliastudios.transcoder.source.DataSource
import java.nio.ByteBuffer
import java.nio.ByteOrder


internal data class ReaderData(val chunk: DataSource.Chunk, val id: Int)

internal interface ReaderChannel : Channel {
    fun buffer(): Pair<ByteBuffer, Int>?
}

internal class Reader(
        private val source: DataSource,
        private val track: TrackType
) : BaseStep<Unit, Channel, ReaderData, ReaderChannel>() {

    override val channel = Channel
    private val chunk = DataSource.Chunk()

    override fun step(state: State.Ok<Unit>): State<ReaderData> {
        return if (!source.canReadTrack(track)) {
            State.Wait
        } else {
            val buffer = next.buffer()
            if (buffer == null) {
                State.Wait
            } else if (source.isDrained) {
                chunk.buffer = buffer.first
                State.Eos(ReaderData(chunk, buffer.second))
            } else {
                chunk.buffer = buffer.first
                source.readTrack(chunk)
                State.Ok(ReaderData(chunk, buffer.second))
            }
        }
    }
}