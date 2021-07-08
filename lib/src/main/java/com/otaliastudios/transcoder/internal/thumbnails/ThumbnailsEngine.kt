package com.otaliastudios.transcoder.internal.thumbnails

import com.otaliastudios.transcoder.ThumbnailerOptions
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest

abstract class ThumbnailsEngine {

    abstract suspend fun queueThumbnails(list: List<ThumbnailRequest>, progress: (Thumbnail) -> Unit)

    abstract fun cleanup()

    companion object {
        private val log = Logger("ThumbnailsEngine")

        private fun Throwable.isInterrupted(): Boolean {
            if (this is InterruptedException) return true
            if (this == this.cause) return false
            return this.cause?.isInterrupted() ?: false
        }

        var engine: ThumbnailsEngine? = null
        private lateinit var dispatcher: ThumbnailsDispatcher

        @JvmStatic
        fun thumbnails(options: ThumbnailerOptions): ThumbnailsEngine? {
            log.i("thumbnails(): called...")
            dispatcher = ThumbnailsDispatcher(options)

             engine = DefaultThumbnailsEngine(
                dataSources = DataSources(options),
                rotation = options.rotation,
                resizer = options.resizer,
                requests = options.thumbnailRequests
            )
            return engine
        }
    }
    suspend fun queue(list: List<ThumbnailRequest>){
        engine?.queueThumbnails(list) {
            dispatcher.dispatchThumbnail(it)
        }

        try {
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