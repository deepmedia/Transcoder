package com.otaliastudios.transcoder.internal.thumbnails

import android.content.Context
import com.otaliastudios.transcoder.ThumbnailerOptions
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import com.otaliastudios.transcoder.thumbnail.Thumbnail

abstract class ThumbnailsEngine {

    abstract fun thumbnails(context: Context,progress: (Thumbnail) -> Unit)

    abstract fun cleanup()

    companion object {
        private val log = Logger("ThumbnailsEngine")

        private fun Throwable.isInterrupted(): Boolean {
            if (this is InterruptedException) return true
            if (this == this.cause) return false
            return this.cause?.isInterrupted() ?: false
        }

        @JvmStatic
        fun thumbnails(context: Context,options: ThumbnailerOptions) {
            log.i("thumbnails(): called...")
            var engine: ThumbnailsEngine? = null
            val dispatcher = ThumbnailsDispatcher(options)
            try {
                engine = DefaultThumbnailsEngine(
                        dataSources = DataSources(options),
                        rotation = options.rotation,
                        resizer = options.resizer,
                        requests = options.thumbnailRequests
                )
                engine.thumbnails(context) {
                    dispatcher.dispatchThumbnail(it)
                }
                dispatcher.dispatchCompletion()
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