@file:Suppress("TooGenericExceptionCaught")

package com.otaliastudios.transcoder.internal.thumbnails

import com.otaliastudios.transcoder.ThumbnailerOptions
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest
import kotlinx.coroutines.flow.Flow

abstract class ThumbnailsEngine {

    abstract val progressFlow: Flow<Thumbnail>

    abstract suspend fun queueThumbnails(list: List<ThumbnailRequest>)

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

}
