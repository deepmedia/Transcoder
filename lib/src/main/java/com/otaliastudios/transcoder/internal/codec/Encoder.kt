package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec.*
import android.media.MediaFormat
import android.view.Surface
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.common.trackType
import com.otaliastudios.transcoder.internal.data.WriterChannel
import com.otaliastudios.transcoder.internal.data.WriterData
import com.otaliastudios.transcoder.internal.media.MediaCodecBuffers
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.QueuedStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource
import java.nio.ByteBuffer

internal data class EncoderData(
        val buffer: ByteBuffer?, // If present, it must have correct position/remaining!
        val id: Int,
        val timeUs: Long
) {
    companion object { val Empty = EncoderData(null, 0, 0L) }
}

internal interface EncoderChannel : Channel {
    val surface: Surface?
    fun buffer(): Pair<ByteBuffer, Int>?
}

internal class Encoder(
        private val format: MediaFormat, // desired output format
) : QueuedStep<EncoderData, EncoderChannel, WriterData, WriterChannel>(), EncoderChannel {

    private val log = Logger("Encoder")
    override val channel = this

    private val codec = createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!).also {
        it.configure(format, null, null, CONFIGURE_FLAG_ENCODE)
    }

    override val surface = when (format.trackType) {
        TrackType.VIDEO -> codec.createInputSurface()
        else -> null
    }

    private val buffers by lazy { MediaCodecBuffers(codec) }

    private var info = BufferInfo()

    init {
        codec.start()
    }

    override fun buffer(): Pair<ByteBuffer, Int>? {
        val id = codec.dequeueInputBuffer(0)
        log.v("buffer(): id=$id")
        return if (id >= 0) buffers.getInputBuffer(id) to id else null
    }

    override fun enqueueEos(data: EncoderData) {
        if (surface != null) codec.signalEndOfInputStream()
        else {
            val flag = BUFFER_FLAG_END_OF_STREAM
            codec.queueInputBuffer(data.id, 0, 0, 0, flag)
        }
    }

    override fun enqueue(data: EncoderData) {
        if (surface != null) return
        else {
            val buffer = requireNotNull(data.buffer) { "Audio should always pass a buffer to Encoder." }
            codec.queueInputBuffer(data.id, buffer.position(), buffer.remaining(), data.timeUs, 0)
        }
    }

    override fun drain(): State<WriterData> {
        return when (val result = codec.dequeueOutputBuffer(info, 0)) {
            INFO_TRY_AGAIN_LATER -> {
                log.e("Can't dequeue output buffer: INFO_TRY_AGAIN_LATER")
                State.Wait
            }
            INFO_OUTPUT_FORMAT_CHANGED -> {
                next.handleFormat(codec.outputFormat)
                State.Retry
            }
            INFO_OUTPUT_BUFFERS_CHANGED -> {
                buffers.onOutputBuffersChanged()
                State.Retry
            }
            else -> {
                val isConfig = info.flags and BUFFER_FLAG_CODEC_CONFIG != 0
                if (isConfig) {
                    codec.releaseOutputBuffer(result, false)
                    State.Retry
                } else {
                    val isEos = info.flags and BUFFER_FLAG_END_OF_STREAM != 0
                    val flags = info.flags and BUFFER_FLAG_END_OF_STREAM.inv()
                    val buffer = buffers.getOutputBuffer(result)
                    val timeUs = info.presentationTimeUs
                    val data = WriterData(buffer, timeUs, flags) {
                        codec.releaseOutputBuffer(result, false)
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                }
            }
        }
    }

    override fun release() {
        codec.stop()
        codec.release()
    }
}