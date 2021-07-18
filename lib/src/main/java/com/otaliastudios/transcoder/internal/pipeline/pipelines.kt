@file:Suppress("MagicNumber", "LongParameterList")

package com.otaliastudios.transcoder.internal.pipeline

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.Codecs
import com.otaliastudios.transcoder.internal.audio.AudioEngine
import com.otaliastudios.transcoder.internal.codec.Decoder
import com.otaliastudios.transcoder.internal.codec.DecoderTimer
import com.otaliastudios.transcoder.internal.codec.Encoder
import com.otaliastudios.transcoder.internal.data.Bridge
import com.otaliastudios.transcoder.internal.data.Reader
import com.otaliastudios.transcoder.internal.data.ReaderTimer
import com.otaliastudios.transcoder.internal.data.Writer
import com.otaliastudios.transcoder.internal.video.VideoPublisher
import com.otaliastudios.transcoder.internal.video.VideoRenderer
import com.otaliastudios.transcoder.resample.AudioResampler
import com.otaliastudios.transcoder.sink.DataSink
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.stretch.AudioStretcher
import com.otaliastudios.transcoder.time.TimeInterpolator

fun emptyPipeline() = Pipeline.build("Empty")

internal fun passThroughPipeline(
    track: TrackType,
    source: DataSource,
    sink: DataSink,
    interpolator: TimeInterpolator
) = Pipeline.build("PassThrough($track)") {
    Reader(source, track) +
        ReaderTimer(track, interpolator) +
        Bridge(source.getTrackFormat(track)!!) +
        Writer(sink, track)
}

internal fun regularPipeline(
    track: TrackType,
    source: DataSource,
    sink: DataSink,
    interpolator: TimeInterpolator,
    format: MediaFormat,
    codecs: Codecs,
    videoRotation: Int,
    audioStretcher: AudioStretcher,
    audioResampler: AudioResampler
) = when (track) {
    TrackType.VIDEO -> videoPipeline(source, sink, interpolator, format, codecs, videoRotation)
    TrackType.AUDIO -> audioPipeline(source, sink, interpolator, format, codecs, audioStretcher, audioResampler)
}

data class RenderingData(
    val timeMsCurrent: Long,
    val timeMsPrevious: Long,
    val updateTime: () -> Unit
)

private fun videoPipeline(
    source: DataSource,
    sink: DataSink,
    interpolator: TimeInterpolator,
    format: MediaFormat,
    codecs: Codecs,
    videoRotation: Int
) = Pipeline.build("Video") {
    Reader(source, TrackType.VIDEO) +
        Decoder(source.getTrackFormat(TrackType.VIDEO)!!, true) +
        DecoderTimer(TrackType.VIDEO, interpolator) +
        VideoRenderer(source.orientation, videoRotation, format) +
        VideoPublisher() +
        Encoder(codecs, TrackType.VIDEO) +
        Writer(sink, TrackType.VIDEO)
}

private fun audioPipeline(
    source: DataSource,
    sink: DataSink,
    interpolator: TimeInterpolator,
    format: MediaFormat,
    codecs: Codecs,
    audioStretcher: AudioStretcher,
    audioResampler: AudioResampler
) = Pipeline.build("Audio") {
    Reader(source, TrackType.AUDIO) +
        Decoder(source.getTrackFormat(TrackType.AUDIO)!!, true) +
        DecoderTimer(TrackType.AUDIO, interpolator) +
        AudioEngine(audioStretcher, audioResampler, format) +
        Encoder(codecs, TrackType.AUDIO) +
        Writer(sink, TrackType.AUDIO)
}
