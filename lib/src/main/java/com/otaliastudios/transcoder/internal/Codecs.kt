package com.otaliastudios.transcoder.internal

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaFormat
import android.opengl.EGL14
import com.otaliastudios.opengl.core.EglCore
import com.otaliastudios.opengl.surface.EglWindowSurface
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.media.MediaFormatConstants
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap
import java.nio.ByteBuffer
import kotlin.properties.Delegates.observable

/**
 * Encoders are shared between segments. This is not strictly needed but it is more efficient
 * and solves timestamp issues that arise due to the fact that MediaCodec can alter the timestamps
 * internally, so if we use different MediaCodec instances we don't have guarantees on monotonic
 * output timestamps, even if input timestamps are. This would later create crashes when passing
 * data to MediaMuxer / MPEG4Writer.
 */
internal class Codecs(
        private val sources: DataSources,
        private val tracks: Tracks,
        private val current: TrackMap<Int>
) {

    class Surface(
        private val context: EglCore,
        val window: EglWindowSurface,
    ) {
        fun release() {
            window.release()
            context.release()
        }
    }

    class Codec(val codec: MediaCodec, val surface: Surface? = null, var log: Logger? = null) {
        var dequeuedInputs by observable(0) { _, _, _ -> log?.v(state) }
        var dequeuedOutputs by observable(0) { _, _, _ -> log?.v(state) }
        val state get(): String = "dequeuedInputs=$dequeuedInputs dequeuedOutputs=$dequeuedOutputs heldInputs=${heldInputs.size}"

        private val heldInputs = ArrayDeque<Pair<ByteBuffer, Int>>()

        fun getInputBuffer(): Pair<ByteBuffer, Int>? {
            if (heldInputs.isNotEmpty()) {
                return heldInputs.removeFirst().also { log?.v(state) }
            }
            val id = codec.dequeueInputBuffer(100)
            return if (id >= 0) {
                dequeuedInputs++
                val buf = checkNotNull(codec.getInputBuffer(id)) { "inputBuffer($id) should not be null." }
                buf to id
            } else {
                log?.i("buffer() failed with $id. $state")
                null
            }
        }

        /**
         * When we're not ready to write into this buffer, it can be held for later.
         * Previously we were returning it to the codec with timestamp=0, flags=0, but especially
         * on older Android versions that can create subtle issues.
         * It's better to just keep the buffer here and reuse it on the next [getInputBuffer] call.
         */
        fun holdInputBuffer(buffer: ByteBuffer, id: Int) {
            heldInputs.addLast(buffer to id)
        }
    }

    private val log = Logger("Codecs")

    val encoders = object : TrackMap<Codec> {

        override fun has(type: TrackType) = tracks.all[type] == TrackStatus.COMPRESSING

        private val lazyAudio by lazy {
            val format = tracks.outputFormats.audio
            val codec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            Codec(codec, null)
        }

        private val lazyVideo by lazy {
            val format = tracks.outputFormats.video
            val width = format.getInteger(MediaFormat.KEY_WIDTH)
            val height = format.getInteger(MediaFormat.KEY_HEIGHT)
            log.i("Destination video surface size: ${width}x${height} @ ${format.getInteger(MediaFormatConstants.KEY_ROTATION_DEGREES)}")
            log.i("Destination video format: $format")

            val allCodecs = MediaCodecList(MediaCodecList.REGULAR_CODECS)
            val videoEncoders = allCodecs.codecInfos.filter { it.isEncoder && it.supportedTypes.any { it.startsWith("video/") } }
            log.i("Available encoders: ${videoEncoders.joinToString { "${it.name} (${it.supportedTypes.joinToString()})" }}")

            // Could consider MediaCodecList(MediaCodecList.REGULAR_CODECS).findEncoderForFormat(format)
            // But it's trickier, for example, format should not include frame rate on API 21 and maybe other quirks.
            val codec = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME)!!)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            log.i("Selected encoder ${codec.name}")
            val surface = codec.createInputSurface()

            log.i("Creating OpenGL context on ${Thread.currentThread()} (${surface.isValid})")
            val eglContext = EglCore(EGL14.EGL_NO_CONTEXT, EglCore.FLAG_RECORDABLE)
            val eglWindow = EglWindowSurface(eglContext, surface, true)
            eglWindow.makeCurrent()

            // On API28 (possibly others) emulator, this happens. If we don't throw early, it fails later with unclear
            // errors - a tombstone dump saying that src.width() & 1 == 0 (basically, complains that surface size is odd)
            // and an error much later on during encoder's dequeue. Surface size is odd because it's 1x1.
            val (eglWidth, eglHeight) = eglWindow.getWidth() to eglWindow.getHeight()
            if (eglWidth != width || eglHeight != height) {
                log.e("OpenGL surface has wrong size (expected: ${width}x${height}, found: ${eglWindow.getWidth()}x${eglWindow.getHeight()}).")
                // Throw a clear error in this very specific scenario so we can catch it in tests.
                if (codec.name == "c2.android.avc.encoder" && eglWidth == 1 && eglHeight == 1) {
                    error("c2.android.avc.encoder was unable to create the input surface (1x1).")
                }
            }

            Codec(codec, Surface(eglContext, eglWindow))
        }

        override fun get(type: TrackType) = when (type) {
            TrackType.AUDIO -> lazyAudio
            TrackType.VIDEO -> lazyVideo
        }
    }

    val ownsEncoderStart = object : TrackMap<Boolean> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType) = current[type] == 0
    }

    val ownsEncoderStop = object : TrackMap<Boolean> {
        override fun has(type: TrackType) = true
        override fun get(type: TrackType) = current[type] == sources[type].lastIndex
    }

    fun release() {
        encoders.forEach {
            it.surface?.release()
        }
    }
}