package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import kotlin.jvm.Throws

internal abstract class Engine {

    @Throws(Exception::class)
    abstract fun transcode()

    companion object {
        @JvmStatic
        fun transcode(options: TranscoderOptions) {
            val engine = DefaultEngine(
                    dataSources = DataSources(options),
                    dataSink = options.dataSink,
                    strategies = trackMapOf(
                            video = options.videoTrackStrategy,
                            audio = options.audioTrackStrategy
                    ),
                    validator = options.validator,
                    videoRotation = options.videoRotation,
                    interpolator = options.timeInterpolator,
                    audioStretcher = options.audioStretcher,
                    audioResampler = options.audioResampler,
                    dispatcher = Dispatcher(options))
            engine.transcode()
        }
    }
}