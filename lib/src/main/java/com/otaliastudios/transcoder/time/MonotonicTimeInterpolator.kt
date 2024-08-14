package com.otaliastudios.transcoder.time

import android.media.MediaMuxer
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.mutableTrackMapOf

/**
 * A [TimeInterpolator] that ensures timestamps are monotonically increasing.
 * Timestamps can go back and forth for many reasons, like miscalculations in MediaCodec output
 * or manually generated timestamps, or at the boundary between one data source and another.
 *
 * Since [MediaMuxer.writeSampleData] can throw in case of invalid timestamps, this interpolator
 * ensures that the next timestamp is at least equal to the previous timestamp plus 1.
 * It does no effort to preserve the input deltas, so the input stream must be as consistent as possible.
 *
 * For example, 20 30 40 50 10 20 30 would become 20 30 40 50 51 52 53.
 */
internal class MonotonicTimeInterpolator : TimeInterpolator {
    private val last = mutableTrackMapOf(Long.MIN_VALUE, Long.MIN_VALUE)
    override fun interpolate(type: TrackType, time: Long): Long {
        return interpolate(last[type], time).also { last[type] = it }
    }
    private fun interpolate(prev: Long, next: Long): Long {
        if (prev == Long.MIN_VALUE) return next
        return next.coerceAtLeast(prev + 1)
    }

}