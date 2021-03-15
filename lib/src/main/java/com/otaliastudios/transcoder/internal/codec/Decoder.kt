package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec.*
import android.media.MediaFormat
import android.view.Surface
import com.otaliastudios.transcoder.internal.data.ReaderChannel
import com.otaliastudios.transcoder.internal.data.ReaderData
import com.otaliastudios.transcoder.internal.media.MediaCodecBuffers
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import java.nio.ByteBuffer


internal data class DecoderData(
        val buffer: ByteBuffer,
        val timeUs: Long,
        val release: (render: Boolean) -> Unit
)

internal interface DecoderChannel : Channel {
    fun handleSourceFormat(format: MediaFormat): Surface?
    fun handleTargetFormat(format: MediaFormat)
}

internal class Decoder(
        private val format: MediaFormat, // source.getTrackFormat(track)
) : BaseStep<ReaderData, ReaderChannel, DecoderData, DecoderChannel>(), ReaderChannel {

    override val channel = this
    private val codec = createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    private val buffers by lazy { MediaCodecBuffers(codec) }
    private var info = BufferInfo()
    private var inputEosSent = false

    override fun initialize(next: DecoderChannel) {
        super.initialize(next)
        val surface = next.handleSourceFormat(format)
        codec.configure(format, surface, null, 0)
        codec.start()
    }

    override fun buffer(): Pair<ByteBuffer, Int>? {
        val id = codec.dequeueInputBuffer(0)
        return if (id >= 0) buffers.getInputBuffer(id) to id else null
    }

    override fun step(state: State.Ok<ReaderData>): State<DecoderData> {
        // Input - feedDecoder
        if (state is State.Eos) {
            if (!inputEosSent) {
                val flag = BUFFER_FLAG_END_OF_STREAM
                codec.queueInputBuffer(state.value.id, 0, 0, 0, flag)
                inputEosSent = true
            }
        } else {
            val (chunk, id) = state.value
            val flag = if (chunk.isKeyFrame) BUFFER_FLAG_SYNC_FRAME else 0
            codec.queueInputBuffer(id, 0, chunk.bytes, chunk.timestampUs, flag)
        }

        // Output - drainDecoder
        val result = codec.dequeueOutputBuffer(info, 0)
        return when (result) {
            INFO_TRY_AGAIN_LATER -> State.Wait
            INFO_OUTPUT_FORMAT_CHANGED -> {
                next.handleTargetFormat(codec.outputFormat)
                State.Retry
            }
            INFO_OUTPUT_BUFFERS_CHANGED -> {
                buffers.onOutputBuffersChanged()
                State.Retry
            }
            else -> {
                val isEos = info.flags and BUFFER_FLAG_END_OF_STREAM != 0
                val hasSize = info.size > 0
                if (isEos || hasSize) {
                    val buffer = buffers.getOutputBuffer(result)
                    val timeUs = info.presentationTimeUs
                    val data = DecoderData(buffer, timeUs) {
                        codec.releaseOutputBuffer(result, it)
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                } else {
                    State.Wait
                }
            }
        }
    }

    override fun release() {
        codec.stop()
        codec.release()
    }
}