package com.otaliastudios.transcoder.thumbnail

class CoverThumbnailRequest : ThumbnailRequest {
    override fun locate(durationUs: Long) = listOf(0L)
}
