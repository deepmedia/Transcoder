package com.otaliastudios.transcoder.internal.video

import com.otaliastudios.transcoder.internal.utils.Logger

internal interface FrameDropper {
    fun shouldRender(timeUs: Long): Boolean
}

/**
 * A very simple dropper, from
 * https://stackoverflow.com/questions/4223766/dropping-video-frames
 */
internal fun FrameDropper(inputFps: Int, outputFps: Int) = object : FrameDropper {

    private val log = Logger("FrameDropper")
    private val inputSpf = 1.0 / inputFps
    private val outputSpf = 1.0 / outputFps
    private var currentSpf = 0.0
    private var frameCount = 0

    override fun shouldRender(timeUs: Long): Boolean {
        currentSpf += inputSpf
        if (frameCount++ == 0) {
            log.v("RENDERING (first frame) - currentSpf=$currentSpf")
            return true
        } else if (currentSpf > outputSpf) {
            currentSpf -= outputSpf
            log.v("RENDERING - currentSpf=$currentSpf")
            return true
        } else {
            log.v("DROPPING - currentSpf=$currentSpf")
            return false
        }
    }
}