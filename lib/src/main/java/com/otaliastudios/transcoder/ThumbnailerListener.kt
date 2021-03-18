package com.otaliastudios.transcoder

import com.otaliastudios.transcoder.thumbnail.Thumbnail

interface ThumbnailerListener {

    fun onThumbnail(thumbnail: Thumbnail)

    fun onThumbnailsCompleted(thumbnails: List<Thumbnail>) = Unit

    fun onThumbnailsCanceled() = Unit

    fun onThumbnailsFailed(exception: Throwable)
}