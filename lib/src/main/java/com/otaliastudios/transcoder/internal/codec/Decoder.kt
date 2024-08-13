package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec.*
import android.media.MediaFormat
import android.view.Surface
import com.otaliastudios.transcoder.common.trackType
import com.otaliastudios.transcoder.internal.data.ReaderChannel
import com.otaliastudios.transcoder.internal.data.ReaderData
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.QueuedStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates
import kotlin.properties.Delegates.observable


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
) : QueuedStep<ReaderData, ReaderChannel, DecoderData, DecoderChannel>("Decoder"), ReaderChannel {

    companion object {
        private val ID = trackMapOf(AtomicInteger(0), AtomicInteger(0))
    }

    private val log = Logger("Decoder(${format.trackType},${ID[format.trackType].getAndIncrement()})")
    override val channel = this

    private val codec = createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
    private var info = BufferInfo()
    private val dropper = DecoderDropper(continuous)

    private var surfaceRendering = false
    private val surfaceRenderingDummyBuffer = ByteBuffer.allocateDirect(0)

    private var dequeuedInputs by observable(0) { _, _, _ -> printDequeued() }
    private var dequeuedOutputs by observable(0) { _, _, _ -> printDequeued() }
    private fun printDequeued() {
        log.v("dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
    }

    override fun initialize(next: DecoderChannel) {
        super.initialize(next)
        log.i("initialize()")
        val surface = next.handleSourceFormat(format)
        surfaceRendering = surface != null
        codec.configure(format, surface, null, 0)
        codec.start()
    }

    override fun buffer(): Pair<ByteBuffer, Int>? {
        val id = codec.dequeueInputBuffer(100)
        return if (id >= 0) {
            dequeuedInputs++
            val buf = checkNotNull(codec.getInputBuffer(id)) { "inputBuffer($id) should not be null." }
            buf to id
        } else {
            log.i("buffer() failed. dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
            null
        }
    }

    override fun enqueueEos(data: ReaderData) {
        log.i("enqueueEos()!")
        dequeuedInputs--
        val flag = BUFFER_FLAG_END_OF_STREAM
        codec.queueInputBuffer(data.id, 0, 0, 0, flag)
    }

    override fun enqueue(data: ReaderData) {
        dequeuedInputs--
        val (chunk, id) = data
        val flag = if (chunk.keyframe) BUFFER_FLAG_SYNC_FRAME else 0
        log.v("enqueued ${chunk.buffer.remaining()} bytes (${chunk.timeUs}us)")
        codec.queueInputBuffer(id, chunk.buffer.position(), chunk.buffer.remaining(), chunk.timeUs, flag)
        dropper.input(chunk.timeUs, chunk.render)
    }

    override fun drain(): State<DecoderData> {
        val result = codec.dequeueOutputBuffer(info, 100)
        return when (result) {
            INFO_TRY_AGAIN_LATER -> {
                log.i("drain(): got INFO_TRY_AGAIN_LATER, waiting.")
                State.Wait(true)
            }
            INFO_OUTPUT_FORMAT_CHANGED -> {
                log.i("drain(): got INFO_OUTPUT_FORMAT_CHANGED, handling format and retrying. format=${codec.outputFormat}")
                next.handleRawFormat(codec.outputFormat)
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
                    val codecBuffer = codec.getOutputBuffer(result)
                    val buffer = when {
                        codecBuffer != null -> codecBuffer
                        surfaceRendering -> surfaceRenderingDummyBuffer // happens, at least on API28 emulator
                        else -> error("outputBuffer($result, ${info.size}, ${info.offset}, ${info.flags}) should not be null.")
                    }
                    dequeuedOutputs++
                    val data = DecoderData(buffer, timeUs) {
                        codec.releaseOutputBuffer(result, it)
                        dequeuedOutputs--
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                } else {
                    // frame was dropped, no need to sleep
                    codec.releaseOutputBuffer(result, false)
                    State.Wait(false)
                }.also {
                    log.v("drain(): returning $it")
                }
            }
        }
    }

    override fun release() {
        log.i("release(): releasing codec. dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
        codec.stop()
        codec.release()
    }
}
