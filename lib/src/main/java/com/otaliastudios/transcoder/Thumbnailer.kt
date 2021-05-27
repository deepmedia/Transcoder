package com.otaliastudios.transcoder

import android.content.Context
import com.otaliastudios.transcoder.internal.thumbnails.ThumbnailsEngine
import com.otaliastudios.transcoder.internal.utils.ThreadPool
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import java.util.concurrent.Callable
import java.util.concurrent.Future

class Thumbnailer private constructor() {

    fun thumbnails(context: Context,options: ThumbnailerOptions): Future<Void> {
        return ThreadPool.executor.submit(Callable {
            ThumbnailsEngine.thumbnails(context,options)
            null
        })
    }

    fun thumbnails(context: Context,builder: ThumbnailerOptions.Builder.() -> Unit) = thumbnails(context,
            options = ThumbnailerOptions.Builder().apply(builder).build()
    )

    companion object {
        // Just for consistency with Transcoder class.
        fun getInstance() = Thumbnailer()
    }
}