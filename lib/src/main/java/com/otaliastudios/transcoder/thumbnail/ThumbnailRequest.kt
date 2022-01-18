package com.otaliastudios.transcoder.thumbnail

interface ThumbnailRequest {
    fun locate(durationUs: Long): List<Long>

    fun threshold(): Long = 0
    // Could make it so that if locate() is empty, accept is called for each frame (no seeking).
    // But this only makes sense if accept signature has more information (segment, ...), and
    // it should also have a way to say - we're done, stop transcoding.
    // fun accept(positionUs: Long): Boolean

    // Could add resizing per request
    // val resizer = PassThroughResizer()
}
