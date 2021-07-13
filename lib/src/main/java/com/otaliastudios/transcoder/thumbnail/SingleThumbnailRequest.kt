package com.otaliastudios.transcoder.thumbnail

open class SingleThumbnailRequest(private val positionUs: Long) : ThumbnailRequest {
    override fun locate(durationUs: Long): List<Long> {
        require(positionUs in 0L..durationUs) {
            "Thumbnail position is out of range. position=$positionUs range=${0L..durationUs}"
        }
        return listOf(positionUs)
    }
}
