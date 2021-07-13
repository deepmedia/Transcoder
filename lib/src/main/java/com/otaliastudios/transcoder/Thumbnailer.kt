package com.otaliastudios.transcoder

// import com.otaliastudios.transcoder.internal.thumbnails.ThumbnailsEngine
// import com.otaliastudios.transcoder.internal.utils.ThreadPool
// import java.util.concurrent.Callable
// import java.util.concurrent.Future
//
// class Thumbnailer private constructor() {
//
//    fun thumbnails(options: ThumbnailerOptions): Future<Void> {
//        return ThreadPool.executor.submit(Callable {
//            ThumbnailsEngine.thumbnails(options)
//            null
//        })
//    }
//
//    fun thumbnails(builder: ThumbnailerOptions.Builder.() -> Unit) = thumbnails(
//            options = ThumbnailerOptions.Builder().apply(builder).build()
//    )
//
//    companion object {
//        // Just for consistency with Transcoder class.
//        fun getInstance() = Thumbnailer()
//    }
// }
