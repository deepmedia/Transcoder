package com.otaliastudios.transcoder.internal.video

import android.opengl.EGL14
import com.otaliastudios.opengl.core.EglCore
import com.otaliastudios.opengl.surface.EglWindowSurface
import com.otaliastudios.transcoder.internal.codec.EncoderChannel
import com.otaliastudios.transcoder.internal.codec.EncoderData
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step


class VideoPublisher: Step<Long, Channel, EncoderData, EncoderChannel> {

    override val channel = Channel

    private val core = EglCore(EGL14.EGL_NO_CONTEXT, EglCore.FLAG_RECORDABLE)
    private lateinit var surface: EglWindowSurface

    override fun initialize(next: EncoderChannel) {
        super.initialize(next)
        surface = EglWindowSurface(core, next.surface!!, false)
        surface.makeCurrent()
    }

    override fun step(state: State.Ok<Long>, fresh: Boolean): State<EncoderData> {
        if (state is State.Eos) {
            return State.Eos(EncoderData.Empty)
        } else {
            surface.setPresentationTime(state.value * 1000)
            surface.swapBuffers()
            return State.Ok(EncoderData.Empty)
        }
    }

    override fun release() {
        surface.release()
        core.release()
    }
}