package com.otaliastudios.transcoder.internal.audio

import java.nio.ShortBuffer

private data class Chunk(
        val buffer: ShortBuffer,
        val timeUs: Long,
        val timeStretch: Double,
        val release: () -> Unit
) {
    companion object {
        val Eos = Chunk(ShortBuffer.allocate(0), 0, 0.0, {})
    }
}

/**
 * FIFO queue for audio processing. Check [isEmpty] before [drain].
 * Queuing is needed because, primarily, the output buffer that you have available is not
 * big enough to contain the full processed size, in which case we want to consume only
 * part of the input buffer and keep it available for the next cycle.
 */
internal class ChunkQueue(private val sampleRate: Int, private val channels: Int) {
    private val queue = ArrayDeque<Chunk>()

    fun isEmpty() = queue.isEmpty()

    fun enqueue(buffer: ShortBuffer, timeUs: Long, timeStretch: Double, release: () -> Unit) {
        if (buffer.hasRemaining()) {
            queue.addLast(Chunk(buffer, timeUs, timeStretch, release))
        } else {
            release()
        }
    }

    fun enqueueEos() {
        queue.addLast(Chunk.Eos)
    }

    fun <T> drain(eos: T, action: (buffer: ShortBuffer, timeUs: Long, timeStretch: Double) -> T): T {
        val head = queue.removeFirst()
        if (head === Chunk.Eos) return eos

        val size = head.buffer.remaining()
        val limit = head.buffer.limit()
        val result = action(head.buffer, head.timeUs, head.timeStretch)
        // Action can reduce the limit for any reason. Restore it before comparing sizes.
        head.buffer.limit(limit)
        if (head.buffer.hasRemaining()) {
            val consumed = size - head.buffer.remaining()
            queue.addFirst(head.copy(
                    timeUs = shortsToUs(consumed, sampleRate, channels)
            ))
        } else {
            // buffer consumed!
            head.release()
        }
        return result
    }
}
