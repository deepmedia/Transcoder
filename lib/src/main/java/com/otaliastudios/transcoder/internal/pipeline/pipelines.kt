package com.otaliastudios.transcoder.internal.pipeline

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.audio.AudioEngine
import com.otaliastudios.transcoder.internal.data.*
import com.otaliastudios.transcoder.internal.data.Reader
import com.otaliastudios.transcoder.internal.data.ReaderTimer
import com.otaliastudios.transcoder.internal.data.Writer
import com.otaliastudios.transcoder.internal.codec.Decoder
import com.otaliastudios.transcoder.internal.codec.DecoderTimer
import com.otaliastudios.transcoder.internal.codec.Encoder
import com.otaliastudios.transcoder.internal.video.VideoPublisher
import com.otaliastudios.transcoder.internal.video.VideoRenderer
import com.otaliastudios.transcoder.resample.AudioResampler
import com.otaliastudios.transcoder.sink.DataSink
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.stretch.AudioStretcher
import com.otaliastudios.transcoder.time.TimeInterpolator

internal fun EmptyPipeline() = Pipeline.build("Empty")

internal fun PassThroughPipeline(
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

internal fun RegularPipeline(
        track: TrackType,
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator,
        format: MediaFormat,
        videoRotation: Int,
        audioStretcher: AudioStretcher,
        audioResampler: AudioResampler
) = when (track) {
    TrackType.VIDEO -> VideoPipeline(source, sink, interpolator, format, videoRotation)
    TrackType.AUDIO -> AudioPipeline(source, sink, interpolator, format, audioStretcher, audioResampler)
}

private fun VideoPipeline(
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator,
        format: MediaFormat,
        videoRotation: Int
) = Pipeline.build("Video") {
    Reader(source, TrackType.VIDEO) +
            Decoder(source.getTrackFormat(TrackType.VIDEO)!!, true) +
            DecoderTimer(TrackType.VIDEO, interpolator) +
            VideoRenderer(source.orientation, videoRotation, format) +
            VideoPublisher() +
            Encoder(format) +
            Writer(sink, TrackType.VIDEO)
}

private fun AudioPipeline(
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator,
        format: MediaFormat,
        audioStretcher: AudioStretcher,
        audioResampler: AudioResampler
) = Pipeline.build("Audio") {
    Reader(source, TrackType.AUDIO) +
            Decoder(source.getTrackFormat(TrackType.AUDIO)!!, true) +
            DecoderTimer(TrackType.VIDEO, interpolator) +
            AudioEngine(audioStretcher, audioResampler, format) +
            Encoder(format) +
            Writer(sink, TrackType.AUDIO)
}