package com.otaliastudios.transcoder.internal.utils

import android.media.MediaCodec
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.sink.DataSink
import com.otaliastudios.transcoder.source.DataSource
import java.nio.ByteBuffer

// See https://github.com/natario1/Transcoder/issues/107
internal fun DataSink.ignoringEos(ignore: () -> Boolean): DataSink =
    EosIgnoringDataSink(this, ignore)

private class EosIgnoringDataSink(
    private val sink: DataSink,
    private val ignore: () -> Boolean,
) : DataSink by sink {
    private val info = MediaCodec.BufferInfo()
    override fun writeTrack(type: TrackType, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        if (ignore()) {
            val flags = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
            if (bufferInfo.size > 0 || flags != 0) {
                info.set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, flags)
                sink.writeTrack(type, byteBuffer, info)
            }
        } else {
            sink.writeTrack(type, byteBuffer, bufferInfo)
        }
    }
}

/**
 * When transcoding has more tracks, we need to check if we need to force EOS on some of them.
 * This can happen if the user adds e.g. 1 minute of audio with 20 seconds of video.
 * In this case the video track must be stopped once the audio stops.
 */
internal fun DataSource.forcingEos(force: () -> Boolean): DataSource =
    EosForcingDataSource(this, force)

private class EosForcingDataSource(
    private val source: DataSource,
    private val force: () -> Boolean,
) : DataSource by source {
    override fun isDrained(): Boolean {
        return force() || source.isDrained
    }
}
fun DataSource.ignoringEOS(force: () -> Boolean): DataSource =
    EosIgnoringDataSource(this, force)

private class EosIgnoringDataSource(
    private val source: DataSource,
    private val force: () -> Boolean,
) : DataSource by source {
    override fun isDrained(): Boolean {
        return force() && source.isDrained
    }
}
