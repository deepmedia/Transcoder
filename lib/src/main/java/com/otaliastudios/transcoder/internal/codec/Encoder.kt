package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec.*
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.Codecs
import com.otaliastudios.transcoder.internal.data.WriterChannel
import com.otaliastudios.transcoder.internal.data.WriterData
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.QueuedStep
import com.otaliastudios.transcoder.internal.pipeline.State
import java.nio.ByteBuffer

internal data class EncoderData(
        val buffer: ByteBuffer?, // If present, it must have correct position/remaining!
        val id: Int,
        val timeUs: Long
) {
    companion object { val Empty = EncoderData(null, 0, 0L) }
}

internal interface EncoderChannel : Channel {
    val surface: Codecs.Surface?
    fun buffer(): Pair<ByteBuffer, Int>?
}

internal class Encoder(
    private val encoder: Codecs.Codec,
    ownsCodecStart: Boolean,
    private val ownsCodecStop: Boolean,
) : QueuedStep<EncoderData, EncoderChannel, WriterData, WriterChannel>(
    when (encoder.surface) {
        null -> "AudioEncoder"
        else -> "VideoEncoder"
    }
), EncoderChannel {

    constructor(codecs: Codecs, type: TrackType) : this(
        codecs.encoders[type],
        codecs.ownsEncoderStart[type],
        codecs.ownsEncoderStop[type]
    )

    override val surface: Codecs.Surface? get() = encoder.surface
    override val channel = this

    private var info = BufferInfo()

    init {
        encoder.log = log
        log.i("ownsStart=$ownsCodecStart ownsStop=$ownsCodecStop ${encoder.state}")
        if (ownsCodecStart) {
            encoder.codec.start()
        }
    }

    override fun buffer(): Pair<ByteBuffer, Int>? = encoder.getInputBuffer()

    private var eosReceivedButNotEnqueued = false

    override fun enqueueEos(data: EncoderData) {
        if (surface == null) {
            if (ownsCodecStop) {
                encoder.codec.queueInputBuffer(data.id, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM)
                encoder.dequeuedInputs--
            } else {
                eosReceivedButNotEnqueued = true
                encoder.holdInputBuffer(data.buffer!!, data.id)
            }
        } else {
            if (!ownsCodecStop) eosReceivedButNotEnqueued = true
            else encoder.codec.signalEndOfInputStream()
        }
    }

    override fun enqueue(data: EncoderData) {
        if (surface != null) return
        else {
            val buffer = requireNotNull(data.buffer) { "Audio should always pass a buffer to Encoder." }
            encoder.codec.queueInputBuffer(data.id, buffer.position(), buffer.remaining(), data.timeUs, 0)
            encoder.dequeuedInputs--
        }
    }

    override fun drain(): State<WriterData> {
        val timeoutUs = if (eosReceivedButNotEnqueued) 5000L else 100L
        return when (val result = encoder.codec.dequeueOutputBuffer(info, timeoutUs)) {
            INFO_TRY_AGAIN_LATER -> {
                if (eosReceivedButNotEnqueued) {
                    // Horrible hack. When we don't own the MediaCodec, we can't enqueue EOS so we
                    // can't dequeue them. INFO_TRY_AGAIN_LATER is returned. We assume this means EOS.
                    log.i("Sending fake Eos. ${encoder.state}")
                    val buffer = ByteBuffer.allocateDirect(0)
                    State.Eos(WriterData(buffer, 0L, 0) {})
                } else {
                    log.i("Can't dequeue output buffer: INFO_TRY_AGAIN_LATER")
                    State.Retry(true)
                }
            }
            INFO_OUTPUT_FORMAT_CHANGED -> {
                log.i("INFO_OUTPUT_FORMAT_CHANGED! format=${encoder.codec.outputFormat}")
                next.handleFormat(encoder.codec.outputFormat)
                drain()
            }
            INFO_OUTPUT_BUFFERS_CHANGED -> {
                drain()
            }
            else -> {
                val isConfig = info.flags and BUFFER_FLAG_CODEC_CONFIG != 0
                if (isConfig) {
                    encoder.codec.releaseOutputBuffer(result, false)
                    drain()
                } else {
                    encoder.dequeuedOutputs++
                    val isEos = info.flags and BUFFER_FLAG_END_OF_STREAM != 0
                    val flags = info.flags and BUFFER_FLAG_END_OF_STREAM.inv()
                    val buffer = checkNotNull(encoder.codec.getOutputBuffer(result)) { "outputBuffer($result) should not be null." }
                    val timeUs = info.presentationTimeUs
                    buffer.clear()
                    buffer.limit(info.offset + info.size)
                    buffer.position(info.offset)
                    val data = WriterData(buffer, timeUs, flags) {
                        encoder.codec.releaseOutputBuffer(result, false)
                        encoder.dequeuedOutputs--
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                }
            }
        }
    }

    override fun release() {
        log.i("release(): ownsStop=$ownsCodecStop ${encoder.state}")
        if (ownsCodecStop) {
            encoder.codec.stop()
        }
    }
}
