package com.otaliastudios.transcoder.thumbnail

class UniformThumbnailRequest(private val count: Int) : ThumbnailRequest {

    init {
        require(count >= 2) {
            "At least 2 thumbnails should be requested when using UniformThumbnailRequest."
        }
    }

    override fun locate(durationUs: Long): List<Long> {
        val list = mutableListOf<Long>()
        var positionUs = 0L
        val stepUs = durationUs / (count - 1)
        repeat(count) {
            list.add(positionUs)
            positionUs += stepUs
        }
        return list
    }
}