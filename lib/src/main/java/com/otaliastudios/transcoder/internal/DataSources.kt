package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.ThumbnailerOptions
import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.source.BlankAudioDataSource
import com.otaliastudios.transcoder.source.DataSource

class DataSources constructor(
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

    private var videoSources: List<DataSource> = updateVideoSources(videoSources)
        set(value) {
            field = updateVideoSources(value)
        }

    private var audioSources: List<DataSource> = updateAudioSources(audioSources)
        set(value) {
            field = updateAudioSources(value)
        }

    private fun updateAudioSources(sources: List<DataSource>) : List<DataSource> {
        val valid = sources.count { it.getTrackFormat(TrackType.AUDIO) != null }
        return when (valid) {
            0 -> listOf<DataSource>().also { sources.deinit() }
            sources.size -> sources
            else -> {
                // Some tracks do not have audio, while some do. Replace with BlankAudio.
                audioSources.map { source ->
                    if (source.getTrackFormat(TrackType.AUDIO) != null) source
                    else BlankAudioDataSource(source.durationUs).also { source.deinit() }
                }
            }
        }
    }

    private fun updateVideoSources(sources: List<DataSource>): List<DataSource> {
        val valid = sources.count { it.getTrackFormat(TrackType.VIDEO) != null }
        return when (valid) {
            0 -> listOf<DataSource>().also { sources.deinit() }
            sources.size -> sources
            else -> sources // Tracks will crash
        }
    }

    fun addDataSource(dataSource: DataSource) {
        addVideoDataSource(dataSource)
        addAudioDataSource(dataSource)
    }

    fun addVideoDataSource(dataSource: DataSource) {
        dataSource.init()
        if (dataSource.getTrackFormat(TrackType.VIDEO) != null && dataSource !in videoSources) {
            videoSources = videoSources + dataSource
        }
    }

    fun addAudioDataSource(dataSource: DataSource) {
        dataSource.init()
        if (dataSource.getTrackFormat(TrackType.AUDIO) != null) {
            audioSources = audioSources + dataSource
        }
    }

    fun getVideoSources() = videoSources

    fun removeDataSource(dataSourceId: String) {
        removeAudioDataSource(dataSourceId)
        removeVideoDataSource(dataSourceId)
    }

    fun removeVideoDataSource(dataSourceId: String) {
        val source = videoSources.find { it.mediaId() == dataSourceId }
        if (source?.getTrackFormat(TrackType.VIDEO) != null) {
            videoSources = videoSources - source
            source.releaseTrack(TrackType.VIDEO)
        }
        source?.deinit()
    }

    fun removeAudioDataSource(dataSourceId: String) {
        val source = audioSources.find { it.mediaId() == dataSourceId }
        if (source?.getTrackFormat(TrackType.AUDIO) != null) {
            audioSources = audioSources - source
            source.releaseTrack(TrackType.AUDIO)
        }
        source?.deinit()
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
