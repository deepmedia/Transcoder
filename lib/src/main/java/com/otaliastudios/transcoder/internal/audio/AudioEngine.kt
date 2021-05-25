package com.otaliastudios.transcoder.internal.audio

import android.media.MediaFormat
import android.media.MediaFormat.*
import android.view.Surface
import com.otaliastudios.transcoder.internal.audio.remix.AudioRemixer
import com.otaliastudios.transcoder.internal.codec.*
import com.otaliastudios.transcoder.internal.pipeline.*
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import com.otaliastudios.transcoder.resample.AudioResampler
import com.otaliastudios.transcoder.stretch.AudioStretcher
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Performs audio rendering, from decoder output to encoder input, applying sample rate conversion,
 * remixing, stretching. TODO: With some extra work this could be split in different steps.
 */
class AudioEngine(
        private val stretcher: AudioStretcher,
        private val resampler: AudioResampler,
        private val targetFormat: MediaFormat
): QueuedStep<DecoderData, DecoderChannel, EncoderData, EncoderChannel>(), DecoderChannel {

    companion object {
        private val ID = AtomicInteger(0)
    }
    private val log = Logger("AudioEngine(${ID.getAndIncrement()})")

    override val channel = this
    private val buffers = ShortBuffers()

    private val MediaFormat.sampleRate get() = getInteger(KEY_SAMPLE_RATE)
    private val MediaFormat.channels get() = getInteger(KEY_CHANNEL_COUNT)

    private lateinit var rawFormat: MediaFormat
    private lateinit var chunks: ChunkQueue
    private lateinit var remixer: AudioRemixer

    override fun handleSourceFormat(sourceFormat: MediaFormat): Surface? = null

    override fun handleRawFormat(rawFormat: MediaFormat) {
        log.i("handleRawFormat($rawFormat)")
        this.rawFormat = rawFormat
        remixer = AudioRemixer[rawFormat.channels, targetFormat.channels]
        chunks = ChunkQueue(rawFormat.sampleRate, rawFormat.channels)
    }

    override fun enqueueEos(data: DecoderData) {
        log.i("enqueueEos()")
        data.release(false)
        chunks.enqueueEos()
    }

    override fun enqueue(data: DecoderData) {
        val stretch = (data as? DecoderTimerData)?.timeStretch ?: 1.0
        chunks.enqueue(data.buffer.asShortBuffer(), data.timeUs, stretch) {
            data.release(false)
        }
    }

    override fun drain(): State<EncoderData> {
        if (chunks.isEmpty()) {
            log.i("drain(): no chunks, waiting...")
            return State.Wait
        }
        val (outBytes, outId) = next.buffer() ?: return run {
            log.i("drain(): no next buffer, waiting...")
            State.Wait
        }
        val outBuffer = outBytes.asShortBuffer()
        return chunks.drain(
                eos = State.Eos(EncoderData(outBytes, outId, 0))
        ) { inBuffer, timeUs, stretch ->
            val outSize = outBuffer.remaining()
            val inSize = inBuffer.remaining()

            // Compute the desired output size based on all steps that we'll go through
            var desiredOutSize = ceil(inSize * stretch) // stretch
            desiredOutSize = remixer.getRemixedSize(desiredOutSize.toInt()).toDouble() // remix
            desiredOutSize = ceil(desiredOutSize * targetFormat.sampleRate / rawFormat.sampleRate) // resample

            // See if we have enough room to process the whole input
            val processableSize = if (desiredOutSize <= outSize) inSize else {
                val factor = desiredOutSize / inSize
                floor(outSize / factor).toInt()
            }
            inBuffer.limit(inBuffer.position() + processableSize)

            // Stretching
            val stretchSize = ceil(processableSize * stretch)
            val stretchBuffer = buffers.acquire("stretch", stretchSize.toInt())
            stretcher.stretch(inBuffer, stretchBuffer, rawFormat.channels)
            stretchBuffer.flip()

            // Remix
            val remixSize = remixer.getRemixedSize(stretchSize.toInt())
            val remixBuffer = buffers.acquire("remix", remixSize)
            remixer.remix(stretchBuffer, remixBuffer)
            remixBuffer.flip()

            // Resample
            resampler.resample(
                    remixBuffer, rawFormat.sampleRate,
                    outBuffer, targetFormat.sampleRate,
                    targetFormat.channels)
            outBuffer.flip()

            // Adjust position and dispatch.
            outBytes.clear()
            outBytes.limit(outBuffer.limit() * BYTES_PER_SHORT)
            outBytes.position(outBuffer.position() * BYTES_PER_SHORT)
            State.Ok(EncoderData(outBytes, outId, timeUs))
        }
    }
}