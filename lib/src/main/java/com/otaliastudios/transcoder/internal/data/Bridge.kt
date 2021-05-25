package com.otaliastudios.transcoder.internal.data

import android.media.MediaCodec
import android.media.MediaFormat
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step
import com.otaliastudios.transcoder.internal.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Bridge(private val format: MediaFormat)
    : Step<ReaderData, ReaderChannel, WriterData, WriterChannel>, ReaderChannel {

    private val log = Logger("Bridge")
    private val bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
    private val buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
    override val channel = this

    override fun buffer(): Pair<ByteBuffer, Int> {
        buffer.clear()
        return buffer to 0
    }

    override fun initialize(next: WriterChannel) {
        log.i("initialize(): format=$format")
        next.handleFormat(format)
    }

    // Can't do much about chunk.render, since we don't even decode.
    override fun step(state: State.Ok<ReaderData>, fresh: Boolean): State<WriterData> {
        val (chunk, _) = state.value
        val flags = if (chunk.keyframe) MediaCodec.BUFFER_FLAG_SYNC_FRAME else 0
        val result = WriterData(chunk.buffer, chunk.timeUs, flags) {}
        return if (state is State.Eos) State.Eos(result) else State.Ok(result)
    }
}