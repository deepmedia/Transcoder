@file:Suppress("MagicNumber")

package com.otaliastudios.transcoder.internal.video

import android.graphics.Bitmap
import android.media.MediaFormat
import android.media.MediaFormat.KEY_HEIGHT
import android.media.MediaFormat.KEY_WIDTH
import android.opengl.EGL14
import android.opengl.GLES20
import com.otaliastudios.opengl.core.EglCore
import com.otaliastudios.opengl.core.Egloo
import com.otaliastudios.opengl.surface.EglOffscreenSurface
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.utils.Logger
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VideoSnapshots(
    format: MediaFormat,
    private val fetchRequest: () -> Request?,
    private val onSnapshot: (Long, Bitmap) -> Unit
) : BaseStep<Long, Channel, Long, Channel>() {

    private val log = Logger("VideoSnapshots")
    override val channel = Channel
    private val width = format.getInteger(KEY_WIDTH)
    private val height = format.getInteger(KEY_HEIGHT)
    private val core = EglCore(EGL14.EGL_NO_CONTEXT, EglCore.FLAG_RECORDABLE)
    private val surface = EglOffscreenSurface(core, width, height).also {
        it.makeCurrent()
    }

    override fun step(state: State.Ok<Long>, fresh: Boolean): State<Long> {
        val request = fetchRequest() ?: return state
        val expectedUs = request.pts
        val accuracyUs = request.threshold
        val deltaUs = (expectedUs - state.value)
        if (deltaUs < accuracyUs || (state is State.Eos && expectedUs > state.value)) {
            log.i("Request MATCHED! expectedUs=$expectedUs actualUs=${state.value} deltaUs=$deltaUs")
            val buffer = ByteBuffer.allocateDirect(width * height * 4)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            Egloo.checkGlError("glReadPixels")
            buffer.rewind()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            onSnapshot(state.value, bitmap)
        } else {
            log.v("Request has high delta. expectedUs=$expectedUs actualUs=${state.value} deltaUs=$deltaUs")
        }
        return state
    }

    override fun release() {
        surface.release()
        core.release()
    }
    data class Request(val pts: Long, val threshold: Long)
}
