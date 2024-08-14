package com.otaliastudios.transcoder.internal.video

import android.opengl.EGL14
import com.otaliastudios.opengl.core.EglCore
import com.otaliastudios.opengl.surface.EglWindowSurface
import com.otaliastudios.transcoder.internal.codec.EncoderChannel
import com.otaliastudios.transcoder.internal.codec.EncoderData
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step


internal class VideoPublisher: BaseStep<Long, Channel, EncoderData, EncoderChannel>("VideoPublisher") {

    override val channel = Channel

    override fun advance(state: State.Ok<Long>): State<EncoderData> {
        if (state is State.Eos) {
            return State.Eos(EncoderData.Empty)
        } else {
            val surface = next.surface!!
            surface.window.setPresentationTime(state.value * 1000)
            surface.window.swapBuffers()
            /* val s = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
            val ss = IntArray(2)
            EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), s, EGL14.EGL_WIDTH, ss, 0)
            EGL14.eglQuerySurface(EGL14.eglGetCurrentDisplay(), s, EGL14.EGL_HEIGHT, ss, 1)
            log.e("XXX VideoPublisher.surfaceSize: ${ss[0]}x${ss[1]}") */
            return State.Ok(EncoderData.Empty)
        }
    }
}