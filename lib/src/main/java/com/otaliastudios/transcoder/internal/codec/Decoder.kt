package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec.*
import android.media.MediaFormat
import android.view.Surface
import com.otaliastudios.transcoder.internal.data.ReaderChannel
import com.otaliastudios.transcoder.internal.data.ReaderData
import com.otaliastudios.transcoder.internal.media.MediaCodecBuffers
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.QueuedStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import java.nio.ByteBuffer


internal open class DecoderData(
        val buffer: ByteBuffer,
        val timeUs: Long,
        val release: (render: Boolean) -> Unit
)

internal interface DecoderChannel : Channel {
    fun handleSourceFormat(sourceFormat: MediaFormat): Surface?
    fun handleRawFormat(rawFormat: MediaFormat)
}

internal class Decoder(
        private val format: MediaFormat, // source.getTrackFormat(track)
) : QueuedStep<ReaderData, ReaderChannel, DecoderData, DecoderChannel>(), ReaderChannel {

    private val log = Logger("Decoder")
    override val channel = this
    private val codec = createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    private val buffers by lazy { MediaCodecBuffers(codec) }
    private var info = BufferInfo()
    private val dropper = DecoderDropper()

    override fun initialize(next: DecoderChannel) {
        super.initialize(next)
        val surface = next.handleSourceFormat(format)
        codec.configure(format, surface, null, 0)
        codec.start()
    }

    override fun buffer(): Pair<ByteBuffer, Int>? {
        val id = codec.dequeueInputBuffer(0)
        log.v("buffer(): id=$id")
        return if (id >= 0) buffers.getInputBuffer(id) to id else null
    }

    override fun enqueueEos(data: ReaderData) {
        val flag = BUFFER_FLAG_END_OF_STREAM
        codec.queueInputBuffer(data.id, 0, 0, 0, flag)
    }

    override fun enqueue(data: ReaderData) {
        val (chunk, id) = data
        val flag = if (chunk.keyframe) BUFFER_FLAG_SYNC_FRAME else 0
        codec.queueInputBuffer(id, 0, chunk.bytes, chunk.timeUs, flag)
        dropper.input(chunk.timeUs, chunk.render)
    }

    override fun drain(): State<DecoderData> {
        val result = codec.dequeueOutputBuffer(info, 0)
        return when (result) {
            INFO_TRY_AGAIN_LATER -> State.Wait
            INFO_OUTPUT_FORMAT_CHANGED -> {
                next.handleRawFormat(codec.outputFormat)
                State.Retry
            }
            INFO_OUTPUT_BUFFERS_CHANGED -> {
                buffers.onOutputBuffersChanged()
                State.Retry
            }
            else -> {
                val isEos = info.flags and BUFFER_FLAG_END_OF_STREAM != 0
                val timeUs = if (isEos) 0 else dropper.output(info.presentationTimeUs)
                if (timeUs != null /* && (isEos || info.size > 0) */) {
                    val buffer = buffers.getOutputBuffer(result)
                    val data = DecoderData(buffer, timeUs) {
                        codec.releaseOutputBuffer(result, it)
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                } else {
                    codec.releaseOutputBuffer(result, false)
                    State.Wait
                }
            }
        }.also {
            log.v("Returning $it")
        }
    }

    override fun release() {
        codec.stop()
        codec.release()
    }
}