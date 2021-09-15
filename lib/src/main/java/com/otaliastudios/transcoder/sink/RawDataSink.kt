package com.otaliastudios.transcoder.sink

import android.media.MediaCodec
import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import java.io.FileOutputStream
import java.nio.ByteBuffer

class RawDataSink(outputFilePath: String) : DataSink {

    private var fileOutputStream: FileOutputStream = FileOutputStream(outputFilePath, true)

    override fun setOrientation(orientation: Int) = Unit

    override fun setLocation(latitude: Double, longitude: Double) = Unit

    override fun setTrackStatus(type: TrackType, status: TrackStatus) = Unit

    override fun setTrackFormat(type: TrackType, format: MediaFormat) = Unit

    override fun writeTrack(
        type: TrackType,
        byteBuffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        fileOutputStream.write(byteBuffer.array(), 0, byteBuffer.limit())
    }

    override fun stop() = Unit

    override fun release() {
        fileOutputStream.close()
    }
}
