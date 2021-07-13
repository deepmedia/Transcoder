@file:Suppress("MagicNumber")

package com.otaliastudios.transcoder.internal.utils

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal object ThreadPool {

    /**
     * NOTE: A better maximum pool size (instead of CPU+1) would be the number of MediaCodec
     * instances that the device can handle at the same time. Hard to tell though as that
     * also depends on the codec type / on input data.
     */
    @JvmStatic
    val executor = ThreadPoolExecutor(
        Runtime.getRuntime().availableProcessors() + 1,
        Runtime.getRuntime().availableProcessors() + 1,
        60,
        TimeUnit.SECONDS,
        LinkedBlockingQueue(),
        object : ThreadFactory {
            private val count = AtomicInteger(1)
            override fun newThread(r: Runnable): Thread {
                return Thread(r, "TranscoderThread #" + count.getAndIncrement())
            }
        }
    )
}
