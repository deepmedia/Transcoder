package com.otaliastudios.transcoder.internal.codec

import android.media.MediaCodec.*
import android.media.MediaFormat
import android.view.Surface
import com.otaliastudios.transcoder.internal.data.WriterChannel
import com.otaliastudios.transcoder.internal.data.WriterData
import com.otaliastudios.transcoder.internal.media.MediaCodecBuffers
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State


internal interface EncoderChannel : Channel {
    val surface: Surface?
}

internal class Encoder(
        private val format: MediaFormat, // desired output format
        extraRotation: Int?,
) : BaseStep<Unit, EncoderChannel, WriterData, WriterChannel>(), EncoderChannel {

    override val channel = this

    init {
        // TODO should be done by previous step
        if (extraRotation != null) {
            // Flip the width and height as needed. This means rotating the VideoStrategy rotation
            // by the amount that was set in the TranscoderOptions.
            // It is possible that the format has its own KEY_ROTATION, but we don't care, that will
            // be respected at playback time.
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            val flip = extraRotation % 180 != 0
            format.setInteger(MediaFormat.KEY_WIDTH, if (flip) height else width)
            format.setInteger(MediaFormat.KEY_HEIGHT, if (flip) width else height)
        }
    }

    private val codec = createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!).also {
        it.configure(format, null, null, CONFIGURE_FLAG_ENCODE)
    }

    override val surface = if (extraRotation != null) codec.createInputSurface() else null
    private val buffers by lazy { MediaCodecBuffers(codec) }
    private var info = BufferInfo()
    private var inputEosSent = false

    init {
        // TODO check if we should maybe start after [surface] has been configured
        codec.start()
    }

    override fun step(state: State.Ok<Unit>): State<WriterData> {
        // Input - feedEncoder
        if (surface == null) {
            // Audio handling
            TODO("Do buffer communication with previous audio component!")
        } else {
            // Video handling. Nothing to do unless EOS.
            if (state is State.Eos && !inputEosSent) {
                codec.signalEndOfInputStream()
                inputEosSent = true
            }
        }

        // Output - drainEncoder
        val result = codec.dequeueOutputBuffer(info, 0)
        return when (result) {
            INFO_TRY_AGAIN_LATER -> State.Wait
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
                    val data = WriterData(buffer, timeUs, flags)
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