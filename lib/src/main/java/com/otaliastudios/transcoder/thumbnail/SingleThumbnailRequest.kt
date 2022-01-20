package com.otaliastudios.transcoder.thumbnail

open class SingleThumbnailRequest(private val positionUs: Long) : ThumbnailRequest {
    override fun locate(durationUs: Long): List<Long> {
//        val positionUs = positionUs.coerceIn(0L..durationUs)
        val positionUs = positionUs.coerceIn(0L..durationUs - 135005 - (positionUs / 1000) % 10000)
        return listOf(positionUs)
    }
}
