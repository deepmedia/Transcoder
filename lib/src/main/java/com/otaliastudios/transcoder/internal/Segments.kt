@file:Suppress("ReturnCount")

package com.otaliastudios.transcoder.internal

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.Pipeline
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.mutableTrackMapOf

class Segments(
    private val sources: DataSources,
    private val tracks: Tracks,
    private val factory: (TrackType, Int, TrackStatus, MediaFormat) -> Pipeline,
    private val ownsLifeCycle: Boolean = true
) {

    private val log = Logger("Segments")
    private val current = mutableTrackMapOf<Segment>(null, null)
    val currentIndex = mutableTrackMapOf(-1, -1)
    private val requestedIndex = mutableTrackMapOf(0, 0)

    fun hasNext(type: TrackType): Boolean {
        if (!sources.has(type)) return false
        log.v(
            "hasNext($type): segment=${current.getOrNull(type)} lastIndex=${sources.getOrNull(type)?.lastIndex}" +
                " canAdvance=${current.getOrNull(type)?.canAdvance()}"
        )
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
            // createSegment will make requested == current.
            requestedIndex > currentIndex -> tryCreateSegment(type, requestedIndex)
            // requested == current
            current[type].canAdvance() -> current[type]
            // requested == current, but this segment is finished:
            // create a new one. destroySegment will increase the requested index,
            // so if this is the last one, we'll return null.
            else -> {
                if (ownsLifeCycle) {
                    destroySegment(current[type])
                    next(type)
                } else {
                    null
                }
            }
        }
    }

    fun release() {
        current.videoOrNull()?.let { destroySegment(it) }
        current.audioOrNull()?.let { destroySegment(it) }
    }

    private fun tryCreateSegment(type: TrackType, index: Int): Segment? {
        // Return null if out of bounds, either because segments are over or because the
        // source set does not have sources for this track type.
        val source = sources[type].getOrNull(index) ?: return null
        log.i("tryCreateSegment($type, $index): created!")
        if (tracks.active.has(type)) {
            source.selectTrack(type)
            // TODO this sucks
            // By design, all sources must select all tracks before seeking. We can achieve this
            // here but it still doesn't look right. The other source might be on a different
            // point in the timeline, or it might be hidden because wrapped in a DataSourceWrapper...
            // We have no checks for any of these options and they can all break concatenation.
            val other = when (type) {
                TrackType.AUDIO -> TrackType.VIDEO
                TrackType.VIDEO -> TrackType.AUDIO
            }
            if (tracks.active.has(other) && sources[other].any { it === source }) {
                source.selectTrack(other)
            }
        }
        // Update current index before pipeline creation, for other components
        // who check it during pipeline init.
        currentIndex[type] = index
        val pipeline = factory(
            type,
            index,
            tracks.all[type],
            tracks.outputFormats[type]
        )
        return Segment(
            type = type,
            index = index,
            pipeline = pipeline
        ).also {
            current[type] = it
        }
    }

    private fun destroySegment(segment: Segment) {
        segment.release()
        val source = sources[segment.type][segment.index]
        if (tracks.active.has(segment.type)) {
            source.releaseTrack(segment.type)
        }
        requestedIndex[segment.type] = segment.index + 1
    }
}
