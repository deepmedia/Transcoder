@file:Suppress("TooGenericExceptionCaught")

package com.otaliastudios.transcoder.internal.thumbnails

import com.otaliastudios.transcoder.ThumbnailerOptions
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest
import kotlinx.coroutines.flow.Flow

abstract class ThumbnailsEngine {

    abstract val progressFlow: Flow<Thumbnail>

    abstract fun addDataSource(dataSource: DataSource)

    abstract fun removeDataSource(dataSourceId: String)

    abstract fun updateDataSources(dataSources: List<DataSource>)

    abstract suspend fun queueThumbnails(list: List<ThumbnailRequest>)

    abstract suspend fun removePosition(source: String, positionUs: Long)

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

        private lateinit var dispatcher: ThumbnailsDispatcher

        @JvmStatic
        fun thumbnails(options: ThumbnailerOptions): ThumbnailsEngine {
            log.i("thumbnails(): called...")
            dispatcher = ThumbnailsDispatcher(options)

            val engine = DefaultThumbnailsEngine(
                dataSources = DataSources(options),
                rotation = options.rotation,
                resizer = options.resizer
            )
            return engine
        }
    }

}
