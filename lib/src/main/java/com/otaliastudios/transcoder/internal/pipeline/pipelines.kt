package com.otaliastudios.transcoder.internal.pipeline

import android.media.MediaCodec
import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.data.*
import com.otaliastudios.transcoder.internal.data.Reader
import com.otaliastudios.transcoder.internal.data.ReaderTimer
import com.otaliastudios.transcoder.internal.data.Writer
import com.otaliastudios.transcoder.internal.codec.Decoder
import com.otaliastudios.transcoder.internal.codec.DecoderTimer
import com.otaliastudios.transcoder.internal.codec.Encoder
import com.otaliastudios.transcoder.internal.video.VideoPublisher
import com.otaliastudios.transcoder.internal.video.VideoRenderer
import com.otaliastudios.transcoder.sink.DataSink
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.time.TimeInterpolator

internal fun EmptyPipeline() = Pipeline.Builder().build()

internal fun PassThroughPipeline(
        track: TrackType,
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator
) : Pipeline {
    return Pipeline.Builder()
            .then(Reader(source, track))
            .then(ReaderTimer(track, interpolator))
            .then(ReaderWriterBridge(source.getTrackFormat(track)!!))
            .then(Writer(sink, track))
            .build()
}

internal fun RegularPipeline(
        track: TrackType,
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator,
        format: MediaFormat,
        videoRotation: Int
) = when (track) {
    TrackType.VIDEO -> VideoPipeline(source, sink, interpolator, format, videoRotation)
    TrackType.AUDIO -> AudioPipeline(source, sink, interpolator)
}

private fun VideoPipeline(
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator,
        format: MediaFormat,
        videoRotation: Int
): Pipeline {
    return Pipeline.Builder()
            .then(Reader(source, TrackType.VIDEO))
            .then(Decoder(source.getTrackFormat(TrackType.VIDEO)!!))
            .then(DecoderTimer(TrackType.VIDEO, interpolator))
            .then(VideoRenderer(source.orientation, videoRotation))
            .then(VideoPublisher())
            .then(Encoder(format, videoRotation))
            .then(Writer(sink, TrackType.VIDEO))
            .build()
}

private fun AudioPipeline(
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator
): Pipeline = error("Audio not supported.")