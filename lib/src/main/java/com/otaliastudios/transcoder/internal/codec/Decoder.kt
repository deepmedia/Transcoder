package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec.*
import android.media.MediaFormat
import android.view.Surface
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.common.trackType
import com.otaliastudios.transcoder.internal.Codecs
import com.otaliastudios.transcoder.internal.data.ReaderChannel
import com.otaliastudios.transcoder.internal.data.ReaderData
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.QueuedStep
import com.otaliastudios.transcoder.internal.pipeline.State
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
    continuous: Boolean, // relevant if the source sends no-render chunks. should we compensate or not?
) : QueuedStep<ReaderData, ReaderChannel, DecoderData, DecoderChannel>(
    when (format.trackType) {
        TrackType.VIDEO -> "VideoDecoder"
        TrackType.AUDIO -> "AudioDecoder"
    }
), ReaderChannel {

    override val channel = this

    init {
        log.i("init: instantiating codec...")
    }
    private val decoder = Codecs.Codec(createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!), null, log)
    private var info = BufferInfo()
    private val dropper = DecoderDropper(continuous)

    private var surfaceRendering = false
    private val surfaceRenderingDummyBuffer = ByteBuffer.allocateDirect(0)

    override fun initialize(next: DecoderChannel) {
        super.initialize(next)
        log.i("initialize()")
        val surface = next.handleSourceFormat(format)
        surfaceRendering = surface != null
        decoder.codec.configure(format, surface, null, 0)
        decoder.codec.start()
    }

    override fun buffer(): Pair<ByteBuffer, Int>? = decoder.getInputBuffer()

    override fun enqueueEos(data: ReaderData) {
        log.i("enqueueEos()!")
        decoder.dequeuedInputs--
        decoder.codec.queueInputBuffer(data.id, 0, 0, 0, BUFFER_FLAG_END_OF_STREAM)
    }

    override fun enqueue(data: ReaderData) {
        decoder.dequeuedInputs--
        val (chunk, id) = data
        val flag = if (chunk.keyframe) BUFFER_FLAG_SYNC_FRAME else 0
        log.v("enqueued ${chunk.buffer.remaining()} bytes (${chunk.timeUs}us)")
        decoder.codec.queueInputBuffer(id, chunk.buffer.position(), chunk.buffer.remaining(), chunk.timeUs, flag)
        dropper.input(chunk.timeUs, chunk.render)
    }

    override fun drain(): State<DecoderData> {
        val result = decoder.codec.dequeueOutputBuffer(info, 100)
        return when (result) {
            INFO_TRY_AGAIN_LATER -> {
                log.i("drain(): got INFO_TRY_AGAIN_LATER, waiting.")
                State.Retry(true)
            }
            INFO_OUTPUT_FORMAT_CHANGED -> {
                log.i("drain(): got INFO_OUTPUT_FORMAT_CHANGED, handling format and retrying. format=${decoder.codec.outputFormat}")
                next.handleRawFormat(decoder.codec.outputFormat)
                drain()
            }
            INFO_OUTPUT_BUFFERS_CHANGED -> {
                log.i("drain(): got INFO_OUTPUT_BUFFERS_CHANGED, retrying.")
                drain()
            }
            else -> {
                val isEos = info.flags and BUFFER_FLAG_END_OF_STREAM != 0
                val timeUs = if (isEos) 0 else dropper.output(info.presentationTimeUs)
                if (timeUs != null /* && (isEos || info.size > 0) */) {
                    val codecBuffer = decoder.codec.getOutputBuffer(result)
                    val buffer = when {
                        codecBuffer != null -> codecBuffer
                        surfaceRendering -> surfaceRenderingDummyBuffer // happens, at least on API28 emulator
                        else -> error("outputBuffer($result, ${info.size}, ${info.offset}, ${info.flags}) should not be null.")
                    }
                    decoder.dequeuedOutputs++
                    val data = DecoderData(buffer, timeUs) {
                        decoder.codec.releaseOutputBuffer(result, it)
                        decoder.dequeuedOutputs--
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                } else {
                    // frame was dropped, no need to sleep
                    decoder.codec.releaseOutputBuffer(result, false)
                    State.Retry(false)
                }.also {
                    log.v("drain(): returning $it")
                }
            }
        }
    }

    override fun release() {
        log.i("release: releasing codec. ${decoder.state}")
        decoder.codec.stop()
        decoder.codec.release()
    }
}
