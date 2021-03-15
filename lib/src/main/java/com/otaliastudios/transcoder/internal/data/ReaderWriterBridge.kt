package com.otaliastudios.transcoder.internal.data

import android.media.MediaCodec
import android.media.MediaFormat
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal class ReaderWriterBridge(private val format: MediaFormat)
    : Step<ReaderData, ReaderChannel, WriterData, WriterChannel>, ReaderChannel {

    private val bufferSize = format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE)
    private val buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder())
    override val channel = this

    override fun buffer(): Pair<ByteBuffer, Int> {
        return buffer to 0
    }

    override fun initialize(next: WriterChannel) {
        next.handleFormat(format)
    }

    override fun step(state: State.Ok<ReaderData>): State<WriterData> {
        val (chunk, _) = state.value
        val flags = if (chunk.isKeyFrame) MediaCodec.BUFFER_FLAG_SYNC_FRAME else 0
        val result = WriterData(chunk.buffer, chunk.timestampUs, flags)
        return state.map(result)
    }
}