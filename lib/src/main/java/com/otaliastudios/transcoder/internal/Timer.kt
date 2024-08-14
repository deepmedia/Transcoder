package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.time.TimeInterpolator

internal class Timer(
    private val interpolator: TimeInterpolator,
    private val sources: DataSources,
    private val tracks: Tracks,
    private val current: TrackMap<Int>
) {

    private val log = Logger("Timer")

    private fun List<DataSource>.durationUs(current: Int) = foldIndexed(0L) { index, acc, source ->
        // If source has been drained, readUs can be more precise than durationUs
        acc + if (index < current) source.positionUs else source.durationUs
    }

    private fun List<DataSource>.positionUs(current: Int) = foldIndexed(0L) { index, acc, source ->
        if (index <= current) acc + source.positionUs else acc
    }

    val positionUs = object : TrackMap<Long> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType): Long {
            return if (!tracks.active.has(type)) 0L
            else sources[type].positionUs(current = current[type])
        }
    }

    val durationUs = object : TrackMap<Long> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType): Long {
            return if (!tracks.active.has(type)) 0L
            else sources[type].durationUs(current = current[type])
        }
    }

    val totalDurationUs: Long get() {
        val video = if (tracks.active.hasVideo) durationUs.video else Long.MAX_VALUE
        val audio = if (tracks.active.hasAudio) durationUs.audio else Long.MAX_VALUE
        return minOf(video, audio)
    }

    val progress = object : TrackMap<Double> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType): Double {
            val read = positionUs[type] // 0L if not active
            val total = durationUs[type] // 0L if not active
            return if (total == 0L) 0.0 else read.toDouble() / total
        }
    }

    private val interpolators = mutableMapOf<Pair<TrackType, Int>, SegmentInterpolator>()

    fun localize(type: TrackType, index: Int, positionUs: Long): Long? {
        if (!tracks.active.has(type)) return null
        val behindUs = sources[type]
                .filterIndexed { i, _ -> i < index }
                .durationUs(-1)
        val localizedUs = positionUs - behindUs
        if (localizedUs < 0L) return null
        if (localizedUs > sources[type][index].durationUs) return null
        return localizedUs
    }

    fun interpolator(type: TrackType, index: Int): SegmentInterpolator = interpolators.getOrPut(type to index) {
        SegmentInterpolator(
            log = Logger("${type.displayName}Interpolator$index/${sources[type].size}"),
            user = interpolator,
            previous = if (index == 0) null else interpolator(type, index - 1)
        )
    }

    class SegmentInterpolator(
        private val log: Logger,
        private val user: TimeInterpolator,
        previous: SegmentInterpolator?,
    ) : TimeInterpolator {

        private var inputBase = Long.MIN_VALUE
        private var interpolatedLast = Long.MIN_VALUE
        private var outputLast = Long.MIN_VALUE
        private val outputBase by lazy {
            when (previous) {
                null -> 0L
                // Not interpolated by user, so we give user interpolator a consistent stream.
                // Add a bit of distance just so they're not identical, won't be noticeable.
                else -> previous.outputLast + 1L
            }.also {
                log.i("Found output base timestamp: $it")
            }
        }

        override fun interpolate(type: TrackType, time: Long): Long {
            if (inputBase == Long.MIN_VALUE) inputBase = time
            outputLast = outputBase + (time - inputBase)
            return user.interpolate(type, outputLast).also {
                check(it > interpolatedLast) { "Timestamps must be monotonically increasing: $it, $interpolatedLast" }
                interpolatedLast = it
            }
        }
    }
}