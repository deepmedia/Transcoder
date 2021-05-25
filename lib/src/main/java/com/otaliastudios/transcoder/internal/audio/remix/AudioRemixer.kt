package com.otaliastudios.transcoder.internal.audio.remix

import java.nio.ShortBuffer

/**
 * Remixes audio data. See [DownMixAudioRemixer], [UpMixAudioRemixer] or [PassThroughAudioRemixer]
 * for concrete implementations.
 */
interface AudioRemixer {

    /**
     * Remixes input audio from input buffer into the output buffer.
     * The output buffer is guaranteed to have a [ShortBuffer.remaining] size that is
     * consistent with [getRemixedSize].
     */
    fun remix(inputBuffer: ShortBuffer, outputBuffer: ShortBuffer)

    /**
     * Returns the output size (in shorts) needed to process an input buffer of the
     * given [inputSize] (in shorts).
     */
    fun getRemixedSize(inputSize: Int): Int

    companion object {
        internal operator fun get(inputChannels: Int, outputChannels: Int): AudioRemixer = when {
            inputChannels !in setOf(1, 2) -> error("Input channel count not supported: $inputChannels")
            outputChannels !in setOf(1, 2) -> error("Input channel count not supported: $inputChannels")
            inputChannels < outputChannels -> UpMixAudioRemixer()
            inputChannels > outputChannels -> DownMixAudioRemixer()
            else -> PassThroughAudioRemixer()
        }
    }
}