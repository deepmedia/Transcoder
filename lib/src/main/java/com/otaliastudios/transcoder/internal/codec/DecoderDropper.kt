package com.otaliastudios.transcoder.internal.codec

import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.source.DataSource

/**
 * Hard to read, late-night class that takes decoder input frames along with their render
 * boolean coming from [DataSource.Chunk.render] and understands which periods of time should be
 * rendered and which shouldn't.
 *
 * These ranges are then matched in the decoder raw output frames. Those that do not belong to
 * a render period are dropped, the others are rendered but their timestamp is shifted according
 * to the no-render periods behind.
 */
internal class DecoderDropper {

    private val log = Logger("DecoderDropper")
    private val closedDeltas = mutableMapOf<LongRange, Long>()
    private val closedRanges = mutableListOf<LongRange>()
    private var pendingRange: LongRange? = null

    private var firstInputUs: Long? = null
    private var firstOutputUs: Long? = null

    private fun debug(message: String, important: Boolean = false) {
        /* val full = "$message pendingRangeUs=${pendingRange} firstInputUs=$firstInputUs " +
                "validInputUs=[${closedRanges.joinToString {
                    "$it(deltaUs=${closedDeltas[it]})"
                }}]"
        if (important) log.w(full) else log.v(full) */
    }

    fun input(timeUs: Long, render: Boolean) {
        if (firstInputUs == null) {
            firstInputUs = timeUs
        }
        if (render) {
            debug("INPUT: inputUs=$timeUs")
            if (pendingRange == null) pendingRange = timeUs..Long.MAX_VALUE
            else pendingRange = pendingRange!!.first..timeUs
        } else {
            debug("INPUT: Got SKIPPING input! inputUs=$timeUs")
            if (pendingRange != null && pendingRange!!.last != Long.MAX_VALUE) {
                closedRanges.add(pendingRange!!)
                closedDeltas[pendingRange!!] = if (closedRanges.size >= 2) {
                    pendingRange!!.first - closedRanges[closedRanges.lastIndex - 1].last
                } else 0L
            }
            pendingRange = null
        }
    }

    fun output(timeUs: Long): Long? {
        if (firstOutputUs == null) {
            firstOutputUs = timeUs
        }
        val timeInInputScaleUs = firstInputUs!! + (timeUs - firstOutputUs!!)
        var deltaUs = 0L
        closedRanges.forEach {
            deltaUs += closedDeltas[it]!!
            if (it.contains(timeInInputScaleUs)) {
                debug("OUTPUT: Rendering! outputTimeUs=$timeUs newOutputTimeUs=${timeUs - deltaUs} deltaUs=$deltaUs")
                return timeUs - deltaUs
            }
        }
        if (pendingRange != null && pendingRange!!.last != Long.MAX_VALUE) {
            if (pendingRange!!.contains(timeInInputScaleUs)) {
                if (closedRanges.isNotEmpty()) {
                    deltaUs += pendingRange!!.first - closedRanges.last().last
                }
                debug("OUTPUT: Rendering! outputTimeUs=$timeUs newOutputTimeUs=${timeUs - deltaUs} deltaUs=$deltaUs")
                return timeUs - deltaUs
            }
        }
        debug("OUTPUT: SKIPPING! outputTimeUs=$timeUs", important = true)
        return null
    }
}