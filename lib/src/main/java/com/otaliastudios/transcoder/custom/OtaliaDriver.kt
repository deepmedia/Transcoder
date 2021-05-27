package com.otaliastudios.transcoder.custom

import android.graphics.Point
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import io.invideo.features.avcore.VideoConfig
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class OtaliaDriver(private val videoConfig: VideoConfig) :
    BaseStep<Unit, Channel, RenderingData, RenderingChannel>() {

    override val channel = Channel
    private val log = Logger("Driver")

    private var timestampMs: Long = 0
    private var prevTimestampMs: Long = 0

    private var frameCount: Int = 0

    override fun initialize(next: RenderingChannel) {
        super.initialize(next)
        next.setDimensions(Point(videoConfig.width, videoConfig.height))
    }

    override fun step(state: State.Ok<Unit>, fresh: Boolean): State<RenderingData> {
        return if (timestampMs > videoConfig.duration) {
            log.i("Source is drained! Returning Eos as soon as possible.")
            State.Eos(RenderingData(timestampMs, prevTimestampMs) {})
        } else {
            val data = RenderingData(timestampMs, prevTimestampMs) {
                prevTimestampMs = timestampMs
                frameCount++
                timestampMs = frameCount * 1000L / videoConfig.fps
            }
            return State.Ok(data)
        }

    }
}
