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
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger


internal interface EncoderChannel : Channel {
    val surface: Surface?
}

internal class Encoder(
        private val format: MediaFormat, // desired output format
) : BaseStep<Unit, EncoderChannel, WriterData, WriterChannel>(), EncoderChannel {

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

    override fun step(state: State.Ok<Unit>, fresh: Boolean): State<WriterData> {
        // Input - feedEncoder
        if (fresh) {
            if (surface == null) {
                // Audio handling
                TODO("Do buffer communication with previous audio component!")
            } else {
                // Video handling. Nothing to do unless EOS.
                if (state is State.Eos) codec.signalEndOfInputStream()
            }
        }

        // Output - drainEncoder
        val result = codec.dequeueOutputBuffer(info, 0)
        return when (result) {
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