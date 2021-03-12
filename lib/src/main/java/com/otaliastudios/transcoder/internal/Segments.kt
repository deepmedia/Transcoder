package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.internal.utils.mutableTrackMapOf
import com.otaliastudios.transcoder.transcode.*

internal class Segments(
        private val sources: DataSources,
        private val tracks: Tracks,
        private val factory: (TrackType, Int, TrackStatus) -> TrackTranscoder
) {

    private val current = mutableTrackMapOf<Segment>(null, null)
    val currentIndex = object : TrackMap<Int> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType): Int {
            return current.getOrNull(type)?.index ?: -1
        }
    }

    private val requestedIndex = mutableTrackMapOf(0, 0)

    fun hasNext(type: TrackType): Boolean {
        if (!sources.has(type)) return false
        val segment = current.getOrNull(type) ?: return true // not started
        val lastIndex = sources.getOrNull(type)?.lastIndex ?: return false // no track!
        return segment.canAdvance() || segment.index < lastIndex
    }

    fun hasNext() = hasNext(TrackType.VIDEO) || hasNext(TrackType.AUDIO)

    /**
     * Returns the [Segment] to be consumed. It is null if:
     * - data sources don't have this type
     * - transcoding for this track is over
     */
    fun next(type: TrackType): Segment? {
        val currentIndex = currentIndex[type]
        val requestedIndex = requestedIndex[type]
        return when {
            requestedIndex < currentIndex -> {
                error("Requested index $requestedIndex smaller than $currentIndex.")
            }
            // We need to open a new segment, if possible.
            requestedIndex > currentIndex -> createSegment(type, requestedIndex)
            // requested == current
            current[type].canAdvance() -> current[type]
            // requested == current, but this segment is finished:
            // create a new one. destroySegment will increase the requested index,
            // so if this is the last one, we'll return null.
            else -> {
                destroySegment(current[type])
                next(type)
            }
        }
    }

    fun release() {
        current.videoOrNull()?.let { destroySegment(it) }
        current.audioOrNull()?.let { destroySegment(it) }
    }

    private fun createSegment(type: TrackType, index: Int): Segment? {
        // Return null if out of bounds, either because segments are over or because the
        // source set does not have sources for this track type.
        val source = sources[type].getOrNull(index) ?: return null
        if (tracks.active.has(type)) {
            source.selectTrack(type)
        }
        return Segment(
                type = type,
                index = index,
                transcoder = factory(type, index, tracks.all[type]),
                outputFormat = tracks.outputFormats[type]
        )
    }

    private fun destroySegment(segment: Segment) {
        segment.release()
        val source = sources[segment.type][segment.index]
        source.releaseTrack(segment.type)
        requestedIndex[segment.type] = segment.index + 1
    }
}