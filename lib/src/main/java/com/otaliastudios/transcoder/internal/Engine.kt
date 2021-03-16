package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import kotlin.jvm.Throws

internal abstract class Engine {

    abstract fun validate(): Boolean

    abstract fun transcode(progress: (Double) -> Unit)

    abstract fun cleanup()

    companion object {
        private val log = Logger("Engine")

        private fun Throwable.isInterrupted(): Boolean {
            if (this is InterruptedException) return true
            if (this == this.cause) return false
            return this.cause?.isInterrupted() ?: false
        }

        @JvmStatic
        fun transcode(options: TranscoderOptions) {
            log.i("transcode(): called...")
            var engine: Engine? = null
            val dispatcher = Dispatcher(options)
            try {
                engine = DefaultEngine(
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
                        audioResampler = options.audioResampler
                )
                if (!engine.validate()) {
                    dispatcher.dispatchSuccess(Transcoder.SUCCESS_NOT_NEEDED)
                } else {
                    engine.transcode {
                        dispatcher.dispatchProgress(it)
                    }
                    dispatcher.dispatchSuccess(Transcoder.SUCCESS_TRANSCODED)
                }
            } catch (e: Exception) {
                if (e.isInterrupted()) {
                    log.i("Transcode canceled.", e)
                    dispatcher.dispatchCancel()
                } else {
                    log.e("Unexpected error while transcoding.", e)
                    dispatcher.dispatchFailure(e)
                    throw e
                }
            } finally {
                engine?.cleanup()
            }
        }
    }
}