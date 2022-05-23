@file:Suppress("ReturnCount")

package com.otaliastudios.transcoder.internal

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.pipeline.Pipeline
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.mutableTrackMapOf
import com.otaliastudios.transcoder.source.DataSource

class CustomSegments(
    private val sources: DataSources,
    private val tracks: Tracks,
    private val factory: (TrackType, Int, TrackStatus, MediaFormat) -> Pipeline,
) {

    private val log = Logger("Segments")
    private var currentSegment: Segment? = null
    private var currentSegmentMapKey: String? = null
    val currentIndex = mutableTrackMapOf(-1, -1)
    private val segmentMap = mutableMapOf<String, Segment?>()

    fun hasNext(type: TrackType): Boolean {
        if (!sources.has(type)) return false
        log.v(
            "hasNext($type): segment=${currentSegment} lastIndex=${sources.getOrNull(type)?.lastIndex}" +
                    " canAdvance=${currentSegment?.canAdvance()}"
        )
        val segment = currentSegment ?: return true // not started
        val lastIndex = sources.getOrNull(type)?.lastIndex ?: return false // no track!
        return segment.canAdvance() || segment.index < lastIndex
    }

    fun hasNext() = hasNext(TrackType.VIDEO)


    // it will be time dependent
    // 1. make segments work for thumbnails as is
    // 2. inject segments dynamically
    // 3. seek to segment and destroy previous ones
    // 4. destroy only if necessary, else reuse

    fun getSegment(id: String): Segment? {
        return segmentMap.getOrPut(id) {
            destroySegment()
            tryCreateSegment(id).also {
                currentSegment = it
                currentSegmentMapKey = id
            }
        }
    }

    fun releaseSegment(id: String) {
        val segment = segmentMap[id]
        segment?.let {
            it.release()
            val source = sources[it.type][it.index]
            if (tracks.active.has(it.type)) {
                source.releaseTrack(it.type)
            }
            segmentMap[id] = null
        }
    }

    fun release() = destroySegment(true)

    private fun tryCreateSegment(id: String): Segment? {
        val index = sources[TrackType.VIDEO].indexOfFirst { it.mediaId() == id }
        // Return null if out of bounds, either because segments are over or because the
        // source set does not have sources for this track type.
        val source = sources[TrackType.VIDEO].getOrNull(index) ?: return null
        source.init()
        log.i("tryCreateSegment(${TrackType.VIDEO}, $index): created!")
        if (tracks.active.has(TrackType.VIDEO)) {
            source.selectTrack(TrackType.VIDEO)
        }
        // Update current index before pipeline creation, for other components
        // who check it during pipeline init.
        currentIndex[TrackType.VIDEO] = index
        val pipeline = factory(
            TrackType.VIDEO,
            index,
            tracks.all[TrackType.VIDEO],
            tracks.outputFormats[TrackType.VIDEO]
        )
        return Segment(TrackType.VIDEO, index, pipeline)
    }

    private fun destroySegment(releaseAll: Boolean = false) {
        currentSegment?.let {
            it.release()
            val source = sources[it.type][it.index]
            if (tracks.active.has(it.type)) {
                source.releaseTrack(it.type)
            }
            currentSegmentMapKey?.let {
                segmentMap[it] = null
            }
            if(releaseAll) {
                segmentMap.clear()
            }
        }
    }
    private fun DataSource.init() = if (!isInitialized) initialize() else Unit

    private fun DataSource.deinit() = if (isInitialized) deinitialize() else Unit

}
