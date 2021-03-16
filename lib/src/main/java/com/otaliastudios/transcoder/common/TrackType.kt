package com.otaliastudios.transcoder.common

import android.media.MediaFormat

enum class TrackType {
    AUDIO, VIDEO
}

internal val MediaFormat.trackType get() = requireNotNull(trackTypeOrNull) {
    "Unexpected mime type: ${getString(MediaFormat.KEY_MIME)}"
}

internal val MediaFormat.trackTypeOrNull get() = when {
    getString(MediaFormat.KEY_MIME)!!.startsWith("audio/") -> TrackType.AUDIO
    getString(MediaFormat.KEY_MIME)!!.startsWith("video/") -> TrackType.VIDEO
    else -> null
}