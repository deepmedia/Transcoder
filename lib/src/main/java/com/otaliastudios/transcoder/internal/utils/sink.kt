package com.otaliastudios.transcoder.internal.utils

import android.media.MediaCodec
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.sink.DataSink
import java.nio.ByteBuffer

// See https://github.com/natario1/Transcoder/issues/107
internal fun DataSink.ignoringEos(): DataSink = EosIgnoringDataSink(this)

private class EosIgnoringDataSink(private val source: DataSink) : DataSink by source {
    private val info = MediaCodec.BufferInfo()
    override fun writeTrack(type: TrackType, byteBuffer: ByteBuffer, bufferInfo: MediaCodec.BufferInfo) {
        val flags = bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM.inv()
        info.set(bufferInfo.offset, bufferInfo.size, bufferInfo.presentationTimeUs, flags)
        source.writeTrack(type, byteBuffer, info)
    }
}