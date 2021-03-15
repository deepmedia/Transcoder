package com.otaliastudios.transcoder.internal.video

import android.media.MediaFormat
import android.media.MediaFormat.*
import android.view.Surface
import com.otaliastudios.transcoder.internal.codec.DecoderChannel
import com.otaliastudios.transcoder.internal.codec.DecoderData
import com.otaliastudios.transcoder.internal.media.MediaFormatConstants.KEY_ROTATION_DEGREES
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step


internal class VideoRenderer(
        private val sourceRotation: Int,
        private val extraRotation: Int
): Step<DecoderData, DecoderChannel, Long, Channel>, DecoderChannel {

    private lateinit var frameDropper: FrameDropper
    private val frameDrawer by lazy {
        FrameDrawer().apply {
            // The rotation we should apply is the intrinsic source rotation, plus any extra
            // rotation that was set into the TranscoderOptions.
            setRotation((sourceRotation + extraRotation) % 360)
        }
    }
    private lateinit var sourceFormat: MediaFormat

    override val channel = this

    // VideoTrackTranscoder.onConfigureDecoder
    override fun handleSourceFormat(format: MediaFormat): Surface {
        sourceFormat = format
        // Just a sanity check that the rotation coming from DataSource is not different from
        // the one found in the DataSource's MediaFormat for video.
        val sourceRotation = runCatching { format.getInteger(KEY_ROTATION_DEGREES) }.getOrElse { 0 }
        if (sourceRotation != this.sourceRotation) {
            error("Unexpected difference in rotation. " +
                    "DataSource=${this.sourceRotation}, MediaFormat=$sourceRotation, " +
                    "hasKey=${format.containsKey(KEY_ROTATION_DEGREES)}, full=$sourceFormat")
        }
        // Decoded video is rotated automatically in Android 5.0 lollipop. Turn off here because we don't want to encode rotated one.
        // refer: https://android.googlesource.com/platform/frameworks/av/+blame/lollipop-release/media/libstagefright/Utils.cpp
        format.setInteger(KEY_ROTATION_DEGREES, 0)
        return frameDrawer.surface
    }

    override fun handleTargetFormat(format: MediaFormat) {
        frameDropper = FrameDropper(
                sourceFormat.getInteger(KEY_FRAME_RATE),
                format.getInteger(KEY_FRAME_RATE))

        // Cropping support.
        // Ignoring any outputFormat KEY_ROTATION (which is applied at playback time), the rotation
        // difference between input and output is mSourceRotation + mExtraRotation.
        val rotation = (sourceRotation + extraRotation) % 360
        val flip = rotation % 180 != 0
        val inputWidth = sourceFormat.getInteger(KEY_WIDTH).toFloat()
        val inputHeight = sourceFormat.getInteger(KEY_HEIGHT).toFloat()
        val inputRatio = inputWidth / inputHeight
        val outputWidth = (if (flip) format.getInteger(KEY_HEIGHT) else format.getInteger(KEY_WIDTH)).toFloat()
        val outputHeight = (if (flip) format.getInteger(KEY_WIDTH) else format.getInteger(KEY_HEIGHT)).toFloat()
        val outputRatio = outputWidth / outputHeight
        var scaleX = 1f
        var scaleY = 1f
        if (inputRatio > outputRatio) { // Input wider. We have a scaleX.
            scaleX = inputRatio / outputRatio
        } else if (inputRatio < outputRatio) { // Input taller. We have a scaleY.
            scaleY = outputRatio / inputRatio
        }
        frameDrawer.setScale(scaleX, scaleY)
    }

    override fun step(state: State.Ok<DecoderData>): State<Long> {
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
                State.Wait
            }
        }
    }

    override fun release() {
        frameDrawer.release()
    }
}