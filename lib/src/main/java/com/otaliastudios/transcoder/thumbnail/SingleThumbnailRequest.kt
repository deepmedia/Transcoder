package com.otaliastudios.transcoder.thumbnail

@Suppress("MagicNumber")
open class SingleThumbnailRequest(private val positionUs: Long) : ThumbnailRequest {
    override fun locate(durationUs: Long): List<Long> {
        val randomizer = (positionUs / 1000) % 10000
        val positionUs = positionUs.coerceIn(0L..durationUs - 135005 - randomizer)
        return listOf(positionUs)
    }
}
