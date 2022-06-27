package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec
import android.media.MediaCodec.*
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import android.view.Surface
import com.otaliastudios.transcoder.common.trackType
import com.otaliastudios.transcoder.internal.data.ReaderChannel
import com.otaliastudios.transcoder.internal.data.ReaderData
import com.otaliastudios.transcoder.internal.media.MediaCodecBuffers
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.QueuedStep
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicInteger
import kotlin.properties.Delegates.observable

open class DecoderData(
    val buffer: ByteBuffer,
    val timeUs: Long,
    val release: (render: Boolean) -> Unit
)

interface DecoderChannel : Channel {
    fun handleSourceFormat(sourceFormat: MediaFormat): Surface?
    fun handleRawFormat(rawFormat: MediaFormat)
}

class Decoder(
    private val format: MediaFormat, // source.getTrackFormat(track)
    continuous: Boolean, // relevant if the source sends no-render chunks. should we compensate or not?
    useSwFor4K: Boolean = false,
    val shouldFlush: (() -> Boolean)? = null
) : QueuedStep<ReaderData, ReaderChannel, DecoderData, DecoderChannel>(), ReaderChannel {

    companion object {
        private val ID = trackMapOf(AtomicInteger(0), AtomicInteger(0))
        private const val timeoutUs = 2000L
        private const val VERBOSE = false
    }

    private val log = Logger("Decoder(${format.trackType},${ID[format.trackType].getAndIncrement()})")
    override val channel = this

    private val codec = createDecoderByType(format, useSwFor4K && format.is4K())

    @Suppress("MagicNumber")
    private fun MediaFormat.is4K(): Boolean {
        val width = format.getInteger(MediaFormat.KEY_WIDTH)
        val height = format.getInteger(MediaFormat.KEY_HEIGHT)
        return width * height > 2120 * 2120
    }

    private fun MediaCodecInfo.isHardwareAcceleratedCompat() : Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            isHardwareAccelerated
        } else {
            val codecName = name.lowercase()
            val isSoftware = (codecName.startsWith("omx.google.")
                    || codecName.startsWith("omx.ffmpeg.")
                    || (codecName.startsWith("omx.sec.") && codecName.contains(".sw."))
                    || codecName == "omx.qcom.video.decoder.hevcswvdec"
                    || codecName.startsWith("c2.android.")
                    || codecName.startsWith("c2.google.")
                    || (!codecName.startsWith("omx.") && !codecName.startsWith("c2.")))

            if (isSoftware) {
                log.i("sw codec: $name")
            }
            !isSoftware
        }
    }

    private fun createDecoderByType(format: MediaFormat, useSoftware: Boolean = false): MediaCodec {
        val mime = format.getString(MediaFormat.KEY_MIME)!!
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val allCodecs = MediaCodecList(MediaCodecList.ALL_CODECS)

            var codecName: String? = null
            for (info in allCodecs.codecInfos) {
                if (info.isEncoder || (info.isHardwareAcceleratedCompat() && useSoftware)) {
                    // log.e("Rejecting codec: ${info.name}")
                    continue
                }
                try {
                    val caps = info.getCapabilitiesForType(mime)
                    if (caps != null && caps.isFormatSupported(format)) {
                        codecName = info.name
                        break
                    } else {
                        // log.e("Rejecting decoder: ${info.name}")
                    }
                } catch (e: IllegalArgumentException) {
                    log.e("Unsupported codec type: $mime")
                }
            }
            log.i("Using codec: $codecName for format: $format")
            return createByCodecName(codecName!!)
        }
        return createDecoderByType(mime)
    }

    private val buffers by lazy { MediaCodecBuffers(codec) }
    private var info = MediaCodec.BufferInfo()
    private val dropper = DecoderDropper(continuous)

    private var dequeuedInputs by observable(0) { _, _, _ -> printDequeued() }
    private var dequeuedOutputs by observable(0) { _, _, _ -> printDequeued() }
    private fun printDequeued() {
        // log.v("dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs")
    }

    override fun initialize(next: DecoderChannel) {
        super.initialize(next)
        log.i("initialize()")
        val surface = next.handleSourceFormat(format)
        codec.configure(format, surface, null, 0)
        codec.start()
    }

    override fun buffer(): Pair<ByteBuffer, Int>? {
        if (shouldFlush?.invoke() == true)
            codec.flush()
        val id = codec.dequeueInputBuffer(0)
        return if (id >= 0) {
            dequeuedInputs++
            buffers.getInputBuffer(id) to id
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
        if(VERBOSE) {
            log.v("enqueue(): queueInputBuffer ${chunk.timeUs}")
        }
        codec.queueInputBuffer(id, chunk.buffer.position(), chunk.buffer.remaining(), chunk.timeUs, flag)
        dropper.input(chunk.timeUs, chunk.render)
    }

    override fun drain(): State<DecoderData> {
        val result = codec.dequeueOutputBuffer(info, timeoutUs)
        return when (result) {
            INFO_TRY_AGAIN_LATER -> {
                log.i("drain(): got INFO_TRY_AGAIN_LATER, waiting.")
                State.Wait
            }
            INFO_OUTPUT_FORMAT_CHANGED -> {
                log.i(
                    "drain(): got INFO_OUTPUT_FORMAT_CHANGED," +
                        " handling format and retrying. format=${codec.outputFormat}"
                )
                next.handleRawFormat(codec.outputFormat)
                State.Retry
            }
            INFO_OUTPUT_BUFFERS_CHANGED -> {
                log.i("drain(): got INFO_OUTPUT_BUFFERS_CHANGED, retrying.")
                buffers.onOutputBuffersChanged()
                State.Retry
            }
            else -> {
                val isEos = info.flags and BUFFER_FLAG_END_OF_STREAM != 0
                val timeUs = if (isEos) 0 else dropper.output(info.presentationTimeUs)
                if (timeUs != null /* && (isEos || info.size > 0) */) {
                    dequeuedOutputs++
                    val buffer = buffers.getOutputBuffer(result)
                    // Ideally, we shouldn't rely on the fact that the buffer is properly configured.
                    // We should configure its position and limit based on the buffer info's position and size.
                    val data = DecoderData(buffer, timeUs) {
                        if(VERBOSE) {
                            log.v("drain(): released successfully presentation ts ${info.presentationTimeUs} and $timeUs")
                        }
                        codec.releaseOutputBuffer(result, it)
                        dequeuedOutputs--
                    }
                    if (isEos) State.Eos(data) else State.Ok(data)
                } else {
                    if(VERBOSE) {
                        log.v("drain(): released because decoder dropper gave null ts ${info.presentationTimeUs}")
                    }
                    codec.releaseOutputBuffer(result, false)
                    State.Wait
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
