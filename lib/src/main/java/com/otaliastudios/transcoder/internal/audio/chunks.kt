package com.otaliastudios.transcoder.internal.audio

import android.media.MediaFormat
import android.media.MediaFormat.KEY_CHANNEL_COUNT
import android.media.MediaFormat.KEY_SAMPLE_RATE
import com.otaliastudios.transcoder.internal.utils.Logger
import java.nio.ByteBuffer
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
internal class ChunkQueue(private val log: Logger) {
    private val queue = ArrayDeque<Chunk>()
    private val pool = ShortBufferPool()

    fun isEmpty() = queue.isEmpty()
    val size get() = queue.size

    fun enqueue(buffer: ShortBuffer, timeUs: Long, timeStretch: Double, release: () -> Unit) {
        if (buffer.hasRemaining()) {
            if (queue.size >= 3) {
                val copy = pool.take(buffer)
                queue.addLast(Chunk(copy, timeUs, timeStretch, { pool.give(copy) }))
                release()
            } else {
                queue.addLast(Chunk(buffer, timeUs, timeStretch, release))
            }
        } else {
            log.w("enqueued invalid buffer ($timeUs, ${buffer.capacity()})")
            release()
        }
    }

    fun enqueueEos() {
        queue.addLast(Chunk.Eos)
    }

    fun <T> drain(format: MediaFormat, eos: T, action: (buffer: ShortBuffer, timeUs: Long, timeStretch: Double) -> T): T {
        val head = queue.removeFirst()
        if (head === Chunk.Eos) return eos

        val size = head.buffer.remaining()
        val limit = head.buffer.limit()
        val result = action(head.buffer, head.timeUs, head.timeStretch)
        // Action can reduce the limit for any reason. Restore it before comparing sizes.
        head.buffer.limit(limit)
        if (head.buffer.hasRemaining()) {
            // We could technically hold onto the same chunk, but in practice it's better to
            // release input buffers back to the decoder otherwise it can get stuck
            val consumed = size - head.buffer.remaining()
            val sampleRate = format.getInteger(KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(KEY_CHANNEL_COUNT)
            val buffer = pool.take(head.buffer)
            head.release()
            queue.addFirst(head.copy(
                timeUs = shortsToUs(consumed, sampleRate, channelCount),
                release = { pool.give(buffer) },
                buffer = buffer
            ))
            log.v("drain(): partially handled chunk at ${head.timeUs}us, ${head.buffer.remaining()} bytes left (${queue.size})")
        } else {
            // buffer consumed!
            log.v("drain(): consumed chunk at ${head.timeUs}us (${queue.size + 1} => ${queue.size})")
            head.release()
        }
        return result
    }
}


class ShortBufferPool {
    private val pool = mutableListOf<ShortBuffer>()

    fun take(original: ShortBuffer): ShortBuffer {
        val needed = original.remaining()
        val index = pool.indexOfFirst { it.capacity() >= needed }
        val memory = when {
            index >= 0 -> pool.removeAt(index)
            else -> ByteBuffer.allocateDirect((needed * Short.SIZE_BYTES).coerceAtLeast(1024))
                .order(original.order())
                .asShortBuffer()
        }
        memory.put(original)
        memory.flip()
        return memory
    }

    fun give(buffer: ShortBuffer) {
        buffer.clear()
        pool.add(buffer)
    }
}