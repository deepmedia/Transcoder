@file:Suppress("MagicNumber")

package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec
import android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG
import android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM
import android.media.MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED
import android.media.MediaCodec.INFO_OUTPUT_FORMAT_CHANGED
import android.media.MediaCodec.INFO_TRY_AGAIN_LATER
import android.view.Surface
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.Codecs
import com.otaliastudios.transcoder.internal.data.WriterChannel
import com.otaliastudios.transcoder.internal.data.WriterData
import com.otaliastudios.transcoder.internal.media.MediaCodecBuffers
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.QueuedStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates.observable

data class EncoderData(
    val buffer: ByteBuffer?, // If present, it must have correct position/remaining!
    val id: Int,
    val timeUs: Long
) {
    companion object { val Empty = EncoderData(null, 0, 0L) }
}

interface EncoderChannel : Channel {
    val surface: Surface?
    fun buffer(): Pair<ByteBuffer, Int>?
}

class Encoder(
    private val codec: MediaCodec,
    override val surface: Surface?,
    ownsCodecStart: Boolean,
    private val ownsCodecStop: Boolean,
) : QueuedStep<EncoderData, EncoderChannel, WriterData, WriterChannel>(), EncoderChannel {

    constructor(codecs: Codecs, type: TrackType) : this(
        codecs.encoders[type].first,
        codecs.encoders[type].second,
        codecs.ownsEncoderStart[type],
        codecs.ownsEncoderStop[type]
    )

    companion object {
        private val ID = trackMapOf(AtomicInteger(0), AtomicInteger(0))
    }

    private val type = if (surface != null) TrackType.VIDEO else TrackType.AUDIO
    private val log = Logger("Encoder($type,${ID[type].getAndIncrement()})")
    private var dequeuedInputs by observable(0) { _, _, _ -> printDequeued() }
    private var dequeuedOutputs by observable(0) { _, _, _ -> printDequeued() }
    private fun printDequeued() {
        log.v("dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
    }

    override val channel = this

    private val buffers by lazy { MediaCodecBuffers(codec) }

    private var info = MediaCodec.BufferInfo()

    init {
        log.i("Encoder: ownsStart=$ownsCodecStart ownsStop=$ownsCodecStop")
        if (ownsCodecStart) {
            codec.start()
        }
    }

    override fun buffer(): Pair<ByteBuffer, Int>? {
        val id = codec.dequeueInputBuffer(0)
        return if (id >= 0) {
            dequeuedInputs++
            buffers.getInputBuffer(id) to id
        } else {
            log.i("buffer() failed. dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
            null
        }
    }

    private var eosReceivedButNotEnqueued = false

    override fun enqueueEos(data: EncoderData) {
        if (surface == null) {
            if (!ownsCodecStop) eosReceivedButNotEnqueued = true
            val flag = if (!ownsCodecStop) 0 else BUFFER_FLAG_END_OF_STREAM
            codec.queueInputBuffer(data.id, 0, 0, 0, flag)
            dequeuedInputs--
        } else {
            if (!ownsCodecStop) eosReceivedButNotEnqueued = true
            else codec.signalEndOfInputStream()
        }
    }

    override fun enqueue(data: EncoderData) {
        if (surface != null) return
        else {
            val buffer = requireNotNull(data.buffer) { "Audio should always pass a buffer to Encoder." }
            codec.queueInputBuffer(data.id, buffer.position(), buffer.remaining(), data.timeUs, 0)
            dequeuedInputs--
        }
    }

    override fun drain(): State<WriterData> {
        val timeoutUs = if (eosReceivedButNotEnqueued) 5000L else 0L
        return when (val result = codec.dequeueOutputBuffer(info, timeoutUs)) {
            INFO_TRY_AGAIN_LATER -> {
                if (eosReceivedButNotEnqueued) {
                    // Horrible hack. When we don't own the MediaCodec, we can't enqueue EOS so we
                    // can't dequeue them. INFO_TRY_AGAIN_LATER is returned. We assume this means EOS.
                    log.i("Sending fake Eos. dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
                    val buffer = ByteBuffer.allocateDirect(0)
                    State.Eos(WriterData(buffer, 0L, 0) {})
                } else {
                    log.i("Can't dequeue output buffer: INFO_TRY_AGAIN_LATER")
                    State.Wait
                }
            }
            INFO_OUTPUT_FORMAT_CHANGED -> {
                log.i("INFO_OUTPUT_FORMAT_CHANGED! format=${codec.outputFormat}")
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
                    dequeuedOutputs++
                    val isEos = info.flags and BUFFER_FLAG_END_OF_STREAM != 0
                    val flags = info.flags and BUFFER_FLAG_END_OF_STREAM.inv()
                    val buffer = buffers.getOutputBuffer(result)
                    val timeUs = if (info.presentationTimeUs < 0) 0 else info.presentationTimeUs
                    if (isEos && timeUs == 0L) {
                        info.offset = 0
                        info.size = 0
                        info.presentationTimeUs = 0
                    }
                    buffer.clear()
                    buffer.limit(info.offset + info.size)
                    buffer.position(info.offset)
                    val data = WriterData(buffer, timeUs, flags) {
                        codec.releaseOutputBuffer(result, false)
                        dequeuedOutputs--
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                }
            }
        }
    }

    override fun release() {
        log.i("release(): ownsStop=$ownsCodecStop dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
        if (ownsCodecStop) {
            codec.stop()
        }
    }
}
