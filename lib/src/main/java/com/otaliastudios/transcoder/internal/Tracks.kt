package com.otaliastudios.transcoder.internal

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.media.MediaFormatProvider
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.strategy.TrackStrategy

internal class Tracks(
        strategies: TrackMap<TrackStrategy>,
        sources: DataSources
) {

    private val log = Logger("Tracks")

    val all: TrackMap<TrackStatus>

    val outputFormats: TrackMap<MediaFormat>

    init {
        val (audioFormat, audioStatus) = resolveTrack(TrackType.AUDIO, strategies.audio, sources.audioOrNull())
        val (videoFormat, videoStatus) = resolveTrack(TrackType.VIDEO, strategies.video, sources.videoOrNull())
        log.i("init: videoStatus=$videoStatus, videoFormat=$videoFormat")
        log.i("init: audioStatus=$audioStatus, audioFormat=$audioFormat")
        all = trackMapOf(video = videoStatus, audio = audioStatus)
        outputFormats = trackMapOf(video = videoFormat, audio = audioFormat)
    }

    val active: TrackMap<TrackStatus> = trackMapOf(
            video = all.video.takeIf { it.isTranscoding },
            audio = all.audio.takeIf { it.isTranscoding }
    )

    private fun resolveTrack(
            type: TrackType,
            strategy: TrackStrategy,
            sources: List<DataSource>? // null or not-empty
    ): Pair<MediaFormat, TrackStatus> {
        return if (sources == null) {
            MediaFormat() to TrackStatus.ABSENT
        } else {
            var status = TrackStatus.ABSENT
            val outputFormat = MediaFormat()
            val provider = MediaFormatProvider()
            val inputFormats = sources.mapNotNull {
                val format = it.getTrackFormat(type)
                format?.run { it to this }
            }.map { (source, format) ->
                provider.provideMediaFormat(source, type, format)
            }
            if (inputFormats.size == sources.size) {
                status = strategy.createOutputFormat(inputFormats, outputFormat)
            } else {
                require(inputFormats.isEmpty()) {
                    "getTrackFormat returned null for ${sources.size - inputFormats.size}" +
                            "/${sources.size} sources of type $type"
                }
            }
            outputFormat to status
        }
    }
}