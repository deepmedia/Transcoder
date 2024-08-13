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
    override val name: String = "Writer"

    private val log = Logger("Writer")
    private val info = MediaCodec.BufferInfo()

    override fun handleFormat(format: MediaFormat) {
        log.i("handleFormat($format)")
        sink.setTrackFormat(track, format)
    }

    override fun step(state: State.Ok<WriterData>, fresh: Boolean): State<Unit> {
        val (buffer, timestamp, flags) = state.value
        // Note: flags does NOT include BUFFER_FLAG_END_OF_STREAM. That's passed via State.Eos.
        val eos = state is State.Eos
        if (eos) {
            // Note: it may happen that at this point, buffer has some data. but creating an extra writeTrack() call
            // can cause some crashes that were not properly debugged, probably related to wrong timestamp.
            // I think if we could ensure that timestamp is valid (> previous, > 0) and buffer.hasRemaining(), there should
            // be an extra call here. See #159. Reluctant to do so without a repro test.
            info.set(0, 0, 0, flags or MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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