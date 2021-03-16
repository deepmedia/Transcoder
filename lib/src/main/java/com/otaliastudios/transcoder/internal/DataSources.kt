package com.otaliastudios.transcoder.internal

import com.otaliastudios.transcoder.TranscoderOptions
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import com.otaliastudios.transcoder.source.DataSource

internal class DataSources private constructor(
        private val videoSources: List<DataSource>,
        private val audioSources: List<DataSource>,
) : TrackMap<List<DataSource>> {

    constructor(options: TranscoderOptions) : this(options.videoDataSources, options.audioDataSources)

    private val log = Logger("DataSources")

    fun all() = (audio + video).distinct()

    override fun get(type: TrackType) = when (type) {
        TrackType.AUDIO -> audioSources
        TrackType.VIDEO -> videoSources
    }

    override fun has(type: TrackType) = this[type].isNotEmpty()

    init {
        log.i("Initializing...")
        all().forEach {
            log.v("Initializing source $it...")
            if (!it.isInitialized) {
                it.initialize()
            }
        }
        log.i("Initialized.")
    }

    fun release() {
        log.i("release(): releasing...")
        all().forEach {
            log.i("release(): releasing source $it...")
            if (it.isInitialized) {
                it.deinitialize()
            }
        }
        log.i("release(): released.")
    }
}