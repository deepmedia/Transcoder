package com.otaliastudios.transcoder.internal.video

import android.media.MediaFormat
import android.media.MediaFormat.*
import android.view.Surface
import com.otaliastudios.transcoder.internal.codec.DecoderChannel
import com.otaliastudios.transcoder.internal.codec.DecoderData
import com.otaliastudios.transcoder.internal.media.MediaFormatConstants.KEY_ROTATION_DEGREES
import com.otaliastudios.transcoder.internal.pipeline.BaseStep
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State


internal class VideoRenderer(
    private val sourceRotation: Int, // intrinsic source rotation
    private val extraRotation: Int, // any extra rotation in TranscoderOptions
    private val targetFormat: MediaFormat,
    flipY: Boolean = false
): BaseStep<DecoderData, DecoderChannel, Long, Channel>("VideoRenderer"), DecoderChannel {

    override val channel = this

    // frame drawer needs EGL context which is not created by us, so let's use by lazy.
    private val frameDrawer by lazy {
        val drawer = FrameDrawer()
        drawer.setFlipY(flipY)
        drawer
    }

    private lateinit var frameDropper: FrameDropper

    init {
        // Modify the target format in place. We apply some extra rotation when drawing,
        // so target format size should be flipped accordingly for correct Encoder configuration.
        // Note that it is possible that format has its own KEY_ROTATION but we don't care, that
        // will be applied at playback time by the player.
        val width = targetFormat.getInteger(KEY_WIDTH)
        val height = targetFormat.getInteger(KEY_HEIGHT)
        val flip = extraRotation % 180 != 0
        val flippedWidth = if (flip) height else width
        val flippedHeight = if (flip) width else height
        targetFormat.setInteger(KEY_WIDTH, flippedWidth)
        targetFormat.setInteger(KEY_HEIGHT, flippedHeight)
        log.i("encoded output format: $targetFormat")
        log.i("output size=${flippedWidth}x${flippedHeight}, flipped=$flip")
    }

    // VideoTrackTranscoder.onConfigureDecoder
    override fun handleSourceFormat(sourceFormat: MediaFormat): Surface {
        log.i("encoded input format: $sourceFormat")

        // Just a sanity check that the rotation coming from DataSource is not different from
        // the one found in the DataSource's MediaFormat for video.
        val sourceRotation = runCatching { sourceFormat.getInteger(KEY_ROTATION_DEGREES) }.getOrElse { 0 }
        if (sourceRotation != this.sourceRotation) {
            error("Unexpected difference in rotation. DataSource=${this.sourceRotation}, MediaFormat=$sourceRotation")
        }

        // Decoded video is rotated automatically starting from Android 5.0. Turn it off here because we
        // don't want to work on the rotated one, we apply rotation at rendering time.
        // https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
        sourceFormat.setInteger(KEY_ROTATION_DEGREES, 0)

        // Instead, apply the source rotation, plus the extra user rotation, to the renderer.
        val rotation = (sourceRotation + extraRotation) % 360
        frameDrawer.setRotation(rotation)

        // Depending on the rotation, we must also pass scale to the drawer due to how GL works.
        val flip = rotation % 180 != 0
        val sourceWidth = sourceFormat.getInteger(KEY_WIDTH).toFloat()
        val sourceHeight = sourceFormat.getInteger(KEY_HEIGHT).toFloat()
        val sourceRatio = sourceWidth / sourceHeight
        val targetWidth = (if (flip) targetFormat.getInteger(KEY_HEIGHT) else targetFormat.getInteger(KEY_WIDTH)).toFloat()
        val targetHeight = (if (flip) targetFormat.getInteger(KEY_WIDTH) else targetFormat.getInteger(KEY_HEIGHT)).toFloat()
        val targetRatio = targetWidth / targetHeight
        var scaleX = 1f
        var scaleY = 1f
        if (sourceRatio > targetRatio) { // Input wider. We have a scaleX.
            scaleX = sourceRatio / targetRatio
        } else if (sourceRatio < targetRatio) { // Input taller. We have a scaleY.
            scaleY = targetRatio / sourceRatio
        }
        frameDrawer.setScale(scaleX, scaleY)

        // Create the frame dropper, now that we know the source FPS and the target FPS.
        frameDropper = FrameDropper(
                sourceFormat.getInteger(KEY_FRAME_RATE),
                targetFormat.getInteger(KEY_FRAME_RATE))
        return frameDrawer.surface
    }

    override fun handleRawFormat(rawFormat: MediaFormat) {
        log.i("decoded input format: $rawFormat")
    }

    override fun advance(state: State.Ok<DecoderData>): State<Long> {
        return if (state is State.Eos) {
            state.value.release(false)
            State.Eos(0L)
        } else {
            if (frameDropper.shouldRender(state.value.timeUs)) {
                state.value.release(true)
                frameDrawer.drawFrame()
                State.Ok(state.value.timeUs)
            } else {
                state.value.release(false)
                State.Consume()
            }
        }
    }

    override fun release() {
        frameDrawer.release()
    }
}
