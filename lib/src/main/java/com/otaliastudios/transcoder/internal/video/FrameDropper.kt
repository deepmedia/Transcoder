@file:Suppress("MagicNumber")

package com.otaliastudios.transcoder.internal.video

import com.otaliastudios.transcoder.internal.utils.Logger

interface FrameDropper {
    fun shouldRender(timeUs: Long): Boolean
}

/**
 * A very simple dropper, from
 * https://stackoverflow.com/questions/4223766/dropping-video-frames
 */
fun frameDropper(inputFps: Int, outputFps: Int) = object : FrameDropper {

    private val log = Logger("FrameDropper")
    private val inputSpf = 1.0 / inputFps
    private val outputSpf = 1.0 / outputFps
    private var currentSpf = 0.0
    private var frameCount = 0
    private var previousTs = 0.0

    override fun shouldRender(timeUs: Long): Boolean {
        val timeS = timeUs / 1000.0 / 1000.0
        currentSpf += timeS - previousTs
        previousTs = timeS
        return when {
            frameCount++ == 0 -> {
                log.v("RENDERING (first frame) - currentSpf=$currentSpf inputSpf=$inputSpf outputSpf=$outputSpf")
                true
            }
            currentSpf > outputSpf -> {
                currentSpf -= outputSpf
                log.v("RENDERING - currentSpf=$currentSpf inputSpf=$inputSpf outputSpf=$outputSpf")
                true
            }
            else -> {
                log.v("DROPPING - currentSpf=$currentSpf inputSpf=$inputSpf outputSpf=$outputSpf")
                false
            }
        }
    }
}
