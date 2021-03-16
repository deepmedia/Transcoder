package com.otaliastudios.transcoder.internal.utils

import com.otaliastudios.transcoder.common.TrackType

interface TrackMap<T> : Iterable<T> {

    operator fun get(type: TrackType): T
    val video get() = get(TrackType.VIDEO)
    val audio get() = get(TrackType.AUDIO)

    fun has(type: TrackType): Boolean
    val hasVideo get() = has(TrackType.VIDEO)
    val hasAudio get() = has(TrackType.AUDIO)

    fun getOrNull(type: TrackType) = if (has(type)) get(type) else null
    fun videoOrNull() = getOrNull(TrackType.VIDEO)
    fun audioOrNull() = getOrNull(TrackType.AUDIO)

    val size get() = listOfNotNull(videoOrNull(), audioOrNull()).size

    override fun iterator() = listOfNotNull(videoOrNull(), audioOrNull()).iterator()
}

interface MutableTrackMap<T> : TrackMap<T> {
    operator fun set(type: TrackType, value: T?)

    fun reset(video: T?, audio: T?) {
        set(TrackType.VIDEO, video)
        set(TrackType.AUDIO, audio)
    }

    override var audio: T
        get() = super.audio
        set(value) = set(TrackType.AUDIO, value)

    override var video: T
        get() = super.video
        set(value) = set(TrackType.VIDEO, value)
}

fun <T> trackMapOf(default: T?) = trackMapOf(default, default)

fun <T> trackMapOf(video: T?, audio: T?): TrackMap<T> = DefaultTrackMap(video, audio)

fun <T> mutableTrackMapOf(default: T?) = mutableTrackMapOf(default, default)

fun <T> mutableTrackMapOf(video: T? = null, audio: T? = null): MutableTrackMap<T> = DefaultTrackMap(video, audio)

private class DefaultTrackMap<T>(video: T?, audio: T?) : MutableTrackMap<T> {
    private val map = mutableMapOf(TrackType.VIDEO to video, TrackType.AUDIO to audio)
    override fun get(type: TrackType): T = requireNotNull(map[type])
    override fun has(type: TrackType) = map[type] != null
    override fun set(type: TrackType, value: T?) {
        map[type] = value
    }
}

