package com.otaliastudios.transcoder.thumbnail

open class SingleThumbnailRequest(private val positionUs: Long) : ThumbnailRequest {
    override fun locate(durationUs: Long): List<Long> {
        val positionUs = positionUs.coerceIn(0L..durationUs)
        return listOf(positionUs)
    }
}
