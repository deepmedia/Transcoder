package com.otaliastudios.transcoder.internal.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ShortBuffer

internal const val BYTES_PER_SHORT = 2

class ShortBuffers {
    private val map = mutableMapOf<String, ShortBuffer>()

    fun acquire(name: String, size: Int): ShortBuffer {
        var current = map[name]
        if (current == null || current.capacity() < size) {
            current = ByteBuffer.allocateDirect(size * BYTES_PER_SHORT)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
        }
        current!!.clear()
        current.limit(size)
        return current.also {
            map[name] = current
        }
    }
}