package com.otaliastudios.transcoder.internal

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap

/**
 * Encoders are shared between segments. This is not strictly needed but it is more efficient
 * and solves timestamp issues that arise due to the fact that MediaCodec can alter the timestamps
 * internally, so if we use different MediaCodec instances we don't have guarantees on monotonic
 * output timestamps, even if input timestamps are. This would later create crashes when passing
 * data to MediaMuxer / MPEG4Writer.
 */
class Codecs(
    private val sources: DataSources,
    private val tracks: Tracks,
    private val current: TrackMap<Int>
) {

    private val log = Logger("Codecs")

    val encoders = object : TrackMap<Pair<MediaCodec, Surface?>> {

        override fun has(type: TrackType) = tracks.all[type] == TrackStatus.COMPRESSING

        private val lazyAudio by lazy {
            val format = tracks.outputFormats.audio
            val codec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec to null
        }

        private val lazyVideo by lazy {
            val format = tracks.outputFormats.video

            val codec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec to codec.createInputSurface()
        }

        override fun get(type: TrackType) = when (type) {
            TrackType.AUDIO -> lazyAudio
            TrackType.VIDEO -> lazyVideo
        }
    }

    val ownsEncoderStart = object : TrackMap<Boolean> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType) = current[type] == 0
    }

    val ownsEncoderStop = object : TrackMap<Boolean> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType) = current[type] == sources[type].lastIndex
    }

    fun release() {
        encoders.forEach {
            it.first.release()
        }
    }
}
