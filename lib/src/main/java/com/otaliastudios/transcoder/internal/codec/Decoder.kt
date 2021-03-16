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
import com.otaliastudios.transcoder.internal.utils.Logger
import java.nio.ByteBuffer


internal data class DecoderData(
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
) : BaseStep<ReaderData, ReaderChannel, DecoderData, DecoderChannel>(), ReaderChannel {

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

    override fun step(state: State.Ok<ReaderData>, fresh: Boolean): State<DecoderData> {
        // Input - feedDecoder
        if (fresh) {
            if (state is State.Eos) {
                val flag = BUFFER_FLAG_END_OF_STREAM
                codec.queueInputBuffer(state.value.id, 0, 0, 0, flag)
            } else {
                val (chunk, id) = state.value
                log.v("feedDecoder(): id=$id isKeyFrame=${chunk.keyframe} bytes=${chunk.bytes} timeUs=${chunk.timeUs} buffer=${chunk.buffer}")
                val flag = if (chunk.keyframe) BUFFER_FLAG_SYNC_FRAME else 0
                codec.queueInputBuffer(id, 0, chunk.bytes, chunk.timeUs, flag)
                dropper.input(chunk.timeUs, chunk.render)
            }
        }

        // Output - drainDecoder
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
                val timeUs = if (isEos) info.presentationTimeUs else dropper.output(info.presentationTimeUs)
                if (timeUs != null /* && (isEos || info.size > 0) */) {
                    val buffer = buffers.getOutputBuffer(result)
                    val data = DecoderData(buffer, timeUs) {
                        codec.releaseOutputBuffer(result, it)
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                } else {
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