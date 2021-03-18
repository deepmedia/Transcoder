package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.ThumbnailerOptions
import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import com.otaliastudios.transcoder.source.BlankAudioDataSource
import com.otaliastudios.transcoder.source.DataSource

internal class DataSources private constructor(
        videoSources: List<DataSource>,
        audioSources: List<DataSource>,
) : TrackMap<List<DataSource>> {

    constructor(options: TranscoderOptions) : this(options.videoDataSources, options.audioDataSources)
    constructor(options: ThumbnailerOptions) : this(options.dataSources, listOf())

    private val log = Logger("DataSources")

    private fun DataSource.init() = if (!isInitialized) initialize() else Unit
    private fun DataSource.deinit() = if (isInitialized) deinitialize() else Unit
    private fun List<DataSource>.init() = forEach { it.init() }
    private fun List<DataSource>.deinit() = forEach { it.deinit() }

    init {
        videoSources.init()
        audioSources.init()
    }

    private val videoSources: List<DataSource> = run {
        val valid = videoSources.count { it.getTrackFormat(TrackType.VIDEO) != null }
        when (valid) {
            0 -> listOf<DataSource>().also { videoSources.deinit() }
            videoSources.size -> videoSources
            else -> videoSources // Tracks will crash
        }
    }

    private val audioSources: List<DataSource> = run {
        val valid = audioSources.count { it.getTrackFormat(TrackType.AUDIO) != null }
        when (valid) {
            0 -> listOf<DataSource>().also { audioSources.deinit() }
            audioSources.size -> audioSources
            else -> {
                // Some tracks do not have audio, while some do. Replace with BlankAudio.
                audioSources.map { source ->
                    if (source.getTrackFormat(TrackType.AUDIO) != null) source
                    else BlankAudioDataSource(source.durationUs).also { source.deinit() }
                }
            }
        }
    }

    override fun get(type: TrackType) = when (type) {
        TrackType.AUDIO -> audioSources
        TrackType.VIDEO -> videoSources
    }

    override fun has(type: TrackType) = this[type].isNotEmpty()

    fun all() = (audio + video).distinct()

    fun release() {
        log.i("release(): releasing...")
        video.forEach { it.deinit() }
        audio.forEach { it.deinit() }
        log.i("release(): released.")
    }
}