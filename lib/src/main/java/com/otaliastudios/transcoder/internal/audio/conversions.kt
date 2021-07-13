@file:Suppress("MagicNumber")

package com.otaliastudios.transcoder.internal.audio

import kotlin.math.ceil

private const val BYTES_PER_SAMPLE_PER_CHANNEL = 2 // Assuming 16bit audio, so 2
private const val MICROSECONDS_PER_SECOND = 1000000L

internal fun bytesToUs(
    bytes: Int /* bytes */,
    sampleRate: Int /* samples/sec */,
    channels: Int /* channel */
): Long {
    val byteRatePerChannel = sampleRate * BYTES_PER_SAMPLE_PER_CHANNEL // bytes/sec/channel
    val byteRate = byteRatePerChannel * channels // bytes/sec
    return MICROSECONDS_PER_SECOND * bytes / byteRate // usec
}

fun bitRate(sampleRate: Int, channels: Int): Int {
    val byteRate = channels * sampleRate * BYTES_PER_SAMPLE_PER_CHANNEL
    return byteRate * 8
}

fun samplesToBytes(samples: Int, channels: Int): Int {
    val bytesPerSample = BYTES_PER_SAMPLE_PER_CHANNEL * channels
    return samples * bytesPerSample
}

internal fun usToBytes(us: Long, sampleRate: Int, channels: Int): Int {
    val byteRatePerChannel = sampleRate * BYTES_PER_SAMPLE_PER_CHANNEL
    val byteRate = byteRatePerChannel * channels
    return ceil(us.toDouble() * byteRate / MICROSECONDS_PER_SECOND).toInt()
}

internal fun shortsToUs(shorts: Int, sampleRate: Int, channels: Int): Long {
    return bytesToUs(shorts * BYTES_PER_SHORT, sampleRate, channels)
}

internal fun usToShorts(us: Long, sampleRate: Int, channels: Int): Int {
    return usToBytes(us, sampleRate, channels) / BYTES_PER_SHORT
}
