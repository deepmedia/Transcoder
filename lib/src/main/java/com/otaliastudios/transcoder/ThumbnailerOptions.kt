package com.otaliastudios.transcoder

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.otaliastudios.transcoder.internal.thumbnails.ThumbnailsEngine
import com.otaliastudios.transcoder.resize.ExactResizer
import com.otaliastudios.transcoder.resize.MultiResizer
import com.otaliastudios.transcoder.resize.Resizer
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.source.FileDescriptorDataSource
import com.otaliastudios.transcoder.source.FilePathDataSource
import com.otaliastudios.transcoder.source.UriDataSource
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest
import java.io.FileDescriptor
import java.util.concurrent.Future

@Suppress("unused")
class ThumbnailerOptions(
        val dataSources: List<DataSource>,
        val resizer: Resizer,
        val rotation: Int,
        val thumbnailRequests: List<ThumbnailRequest>,
        val listener: ThumbnailerListener,
        val listenerHandler: Handler
) {

    class Builder {

        private val dataSources = mutableListOf<DataSource>()
        private val thumbnailRequests = mutableListOf<ThumbnailRequest>()
        private val resizer = MultiResizer()
        private var resizerSet = false
        private var rotation = 0
        private var listener: ThumbnailerListener? = null
        private var listenerHandler: Handler? = null

        fun addDataSource(dataSource: DataSource) = this.also {
            dataSources.add(dataSource)
        }

        fun addDataSource(fileDescriptor: FileDescriptor)
                = addDataSource(FileDescriptorDataSource(fileDescriptor))

        fun addDataSource(filePath: String)
                = addDataSource(FilePathDataSource(filePath))

        fun addDataSource(context: Context, uri: Uri)
                = addDataSource(UriDataSource(context, uri))

        /**
         * Sets the video output strategy. If absent, this defaults to 320x240 images.
         */
        fun addResizer(resizer: Resizer) = this.also {
            this.resizer.addResizer(resizer)
            this.resizerSet = true
        }

        /**
         * The clockwise rotation to be applied to the input video frames.
         * Defaults to 0, which leaves the input rotation unchanged.
         */
        fun setRotation(rotation: Int) = this.also {
            this.rotation = rotation
        }

        fun addThumbnailRequest(request: ThumbnailRequest) = this.also {
            thumbnailRequests.add(request)
        }

        /**
         * Sets an handler for [ThumbnailerListener] callbacks.
         * If null, this will default to the thread that starts the transcoding, if it
         * has a looper, or the UI thread otherwise.
         */
        fun setListenerHandler(listenerHandler: Handler?) = this.also {
            this.listenerHandler = listenerHandler
        }

        fun setListener(listener: ThumbnailerListener) = this.also {
            this.listener = listener
        }

        fun build(): ThumbnailerOptions {
            require(dataSources.isNotEmpty()) {
                "At least one data source is required!"
            }
            require(thumbnailRequests.isNotEmpty()) {
                "At least one thumbnail request is required!"
            }
            val listener = requireNotNull(listener) {
                "Listener can't be null."
            }
            val listenerHandler = listenerHandler
                    ?: Handler(Looper.myLooper() ?: Looper.getMainLooper())
            val resizer = if (resizerSet) resizer else ExactResizer(320, 240)
            return ThumbnailerOptions(
                    dataSources = dataSources.toList(),
                    resizer = resizer,
                    rotation = rotation,
                    thumbnailRequests = thumbnailRequests.toList(),
                    listener = listener,
                    listenerHandler = listenerHandler
            )
        }

        suspend fun thumbnails(): ThumbnailsEngine? {
            return ThumbnailsEngine.thumbnails(build())
        }
    }
}