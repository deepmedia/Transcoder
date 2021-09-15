@file:Suppress("EmptyFunctionBlock", "MagicNumber", "TooManyFunctions")

package com.otaliastudios.transcoder.source

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.common.WavFile
import com.otaliastudios.transcoder.internal.audio.bitRate
import com.otaliastudios.transcoder.internal.audio.samplesToBytes
import com.otaliastudios.transcoder.internal.media.MediaFormatConstants
import java.io.File
import java.io.FileInputStream
import java.io.IOException

class RawAudioDataSource(private val path: String) : DataSource {

    private var fileInputStream: FileInputStream? = null
    private var mInitialized = false
    val mediaFormat = MediaFormat()
    private val b = ByteArray(samplesToBytes(1024, 2))
    private val wavFile = WavFile.openWavFile(File(path))
    private var frameCount: Int = 0

    override fun initialize() {
        fileInputStream = FileInputStream(path)
        mInitialized = true
    }

    override fun deinitialize() {
        mInitialized = false
    }

    override fun isInitialized() = mInitialized

    override fun getOrientation() = 0

    override fun getDurationUs() = 10000000L

    override fun getLocation() = doubleArrayOf(0.0, 0.0)

    override fun selectTrack(type: TrackType) {}

    override fun seekTo(desiredPositionUs: Long) = 0L

    override fun canReadTrack(type: TrackType): Boolean {
        fileInputStream?.let {
            return it.available() > 0
        }
        return false
    }

    var totalRead = 0
    override fun readTrack(chunk: DataSource.Chunk) {
        chunk.buffer.position(0)
        var read = 0
        try {
            read = fileInputStream?.read(b)!!
        } catch (e: IOException) {
            println(e)
        }
        totalRead += read
        val timestampUs = totalRead * 1000L * 1000L / (wavFile.sampleRate * wavFile.numChannels * BYTES_PER_SAMPLE)
//        val timestampUs = frameCount * 1000L * 1000L * SAMPLES_PER_FRAME / wavFile.sampleRate.toFloat()

        chunk.buffer.put(b, 0, read)
        chunk.buffer.position(0)
        chunk.buffer.limit(read)
        chunk.timeUs = timestampUs
        chunk.render = true
        frameCount++
    }

    override fun getPositionUs() = 0L

    override fun isDrained(): Boolean {
        fileInputStream?.let { return it.available() <= 0 }
        return true
    }

    override fun getTrackFormat(type: TrackType): MediaFormat {
        return mediaFormat
    }

    override fun releaseTrack(type: TrackType) {}

    init {
        mediaFormat.setString(MediaFormat.KEY_MIME, MediaFormatConstants.MIMETYPE_AUDIO_RAW)
        mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate(wavFile.sampleRate.toInt(), wavFile.numChannels))
        mediaFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, wavFile.numChannels)
        mediaFormat.setInteger(
            MediaFormat.KEY_MAX_INPUT_SIZE,
            samplesToBytes(BYTES_PER_SAMPLE * SAMPLES_PER_FRAME, wavFile.numChannels)
        )
        mediaFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, wavFile.sampleRate.toInt())
    }

    companion object {
        // AAC frame size. Audio encoder input size is a multiple of this
        private const val SAMPLES_PER_FRAME = 1024
        private const val BYTES_PER_SAMPLE = 2
    }
}
