package com.otaliastudios.transcoder.internal.data

import android.media.MediaCodec
import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.sink.DataSink
import java.nio.ByteBuffer

internal data class WriterData(
        val buffer: ByteBuffer,
        val timeUs: Long,
        val flags: Int,
        val release: () -> Unit
)

internal interface WriterChannel : Channel {
    fun handleFormat(format: MediaFormat)
}

internal class Writer(
        private val sink: DataSink,
        private val track: TrackType
) : Step<WriterData, WriterChannel, Unit, Channel>, WriterChannel {

    override val channel = this

    private val log = Logger("Writer")
    private val info = MediaCodec.BufferInfo()

    override fun handleFormat(format: MediaFormat) {
        log.i("handleFormat($format)")
        sink.setTrackFormat(track, format)
    }

    override fun step(state: State.Ok<WriterData>, fresh: Boolean): State<Unit> {
        val (buffer, timestamp, flags) = state.value
        val eos = state is State.Eos
        if (eos) {
            info.set(0, 0, 0, flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        } else {
            info.set(
                    buffer.position(),
                    buffer.remaining(),
                    timestamp,
                    flags
            )
        }
        sink.writeTrack(track, buffer, info)
        state.value.release()
        return if (eos) State.Eos(Unit) else State.Ok(Unit)
    }
}