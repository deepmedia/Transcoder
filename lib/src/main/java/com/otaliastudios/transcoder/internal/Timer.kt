package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.time.TimeInterpolator

internal class Timer(
        private val interpolator: TimeInterpolator,
        private val sources: DataSources,
        private val tracks: Tracks,
        private val current: TrackMap<Int>
) {

    val readUs = object : TrackMap<Long> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType): Long {
            return if (tracks.active.has(type)) {
                sources[type].foldIndexed(0L) { index, acc, source ->
                    if (index <= current[type]) acc + source.readUs else acc
                }
            } else 0L
        }
    }

    val totalUs = object : TrackMap<Long> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType): Long {
            return if (tracks.active.has(type)) {
                sources[type].foldIndexed(0L) { index, acc, source ->
                    // If source has been drained, readUs can be more precise than durationUs
                    if (index < current[type]) acc + source.readUs else source.durationUs
                }
            } else 0L
        }
    }

    val durationUs: Long get() {
        val video = if (tracks.active.hasVideo) totalUs.video else Long.MAX_VALUE
        val audio = if (tracks.active.hasAudio) totalUs.audio else Long.MAX_VALUE
        return minOf(video, audio)
    }

    val progress = object : TrackMap<Double> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType): Double {
            val read = readUs[type] // 0L if not active
            val total = totalUs[type] // 0L if not active
            return if (total == 0L) 0.0 else read.toDouble() / total
        }
    }

    private val interpolators = mutableMapOf<Pair<TrackType, Int>, TimeInterpolator>()

    fun interpolator(type: TrackType, index: Int) = interpolators.getOrPut(type to index) {
        object : TimeInterpolator {

            private var lastOut = 0L
            private var firstIn = Long.MAX_VALUE
            private val firstOut = when {
                index == 0 -> 0L
                else -> {
                    // Add 10 just so they're not identical.
                    val previous = interpolators[type to index - 1]!!
                    previous.interpolate(type, Long.MAX_VALUE) + 10L
                }
            }

            override fun interpolate(type: TrackType, time: Long) = when (time) {
                Long.MAX_VALUE -> lastOut
                else -> {
                    if (firstIn == Long.MAX_VALUE) firstIn = time
                    lastOut = firstOut + (time - firstIn)
                    interpolator.interpolate(type, lastOut)
                }
            }
        }
    }
}