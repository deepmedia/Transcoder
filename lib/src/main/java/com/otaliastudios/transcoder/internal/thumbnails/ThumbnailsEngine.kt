@file:Suppress("TooGenericExceptionCaught")

package com.otaliastudios.transcoder.internal.thumbnails

import com.otaliastudios.transcoder.ThumbnailerOptions
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest

abstract class ThumbnailsEngine {

    abstract suspend fun queueThumbnails(list: List<ThumbnailRequest>, progress: (Thumbnail) -> Unit)

    abstract suspend fun removePosition(positionUs: Long)

    abstract fun cleanup()

    companion object {
        private val log = Logger("ThumbnailsEngine")

        private fun Throwable.isInterrupted(): Boolean {
            return when {
                this is InterruptedException -> true
                this == this.cause -> false
                else -> this.cause?.isInterrupted() ?: false
            }
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
                resizer = options.resizer
            )
            return engine
        }
    }
    suspend fun queue(list: List<ThumbnailRequest>) {
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
