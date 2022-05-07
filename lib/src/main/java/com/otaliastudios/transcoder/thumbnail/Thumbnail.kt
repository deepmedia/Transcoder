package com.otaliastudios.transcoder.thumbnail

import android.graphics.Bitmap

class Thumbnail constructor(
    val request: ThumbnailRequest,
    val positionUs: Long,
    val bitmap: Bitmap
)
