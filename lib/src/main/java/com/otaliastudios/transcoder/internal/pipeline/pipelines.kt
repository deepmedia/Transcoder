package com.otaliastudios.transcoder.internal.pipeline

import android.content.Context
import android.media.MediaFormat
import android.util.Log
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.Codecs
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
import io.invideo.features.avcore.VideoConfig
import com.otaliastudios.transcoder.custom.OtaliaDriver
import com.otaliastudios.transcoder.custom.RKorge
import io.invideo.renderer.RenderTarget
import io.invideo.renderer.RendererConfiguration
import io.invideo.renderer.rendertree.Size

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
        context: Context,
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
    TrackType.VIDEO -> KorgeVideoPipeline(context,source, sink, interpolator, format, codecs, videoRotation)
    TrackType.AUDIO -> AudioPipeline(source, sink, interpolator, format, codecs, audioStretcher, audioResampler)
}

private fun KorgeVideoPipeline(
        context: Context,
        source: DataSource,
        sink: DataSink,
        interpolator: TimeInterpolator,
        format: MediaFormat,
        codecs: Codecs,
        videoRotation: Int
) = Pipeline.build("Video") {

        // Driver -> Renderer ->
        OtaliaDriver(VideoConfig(720, 720, 10 * 1024 * 1024, duration = 20000, 30)) +
                RKorge(context, RendererConfiguration(
                        Size(720.0, 720.0),
                        RenderTarget.DISPLAY
                ) { Log.d("Pipeline", "Renderer Initialization complete") }, null
                ) +
                VideoPublisher() +
                Encoder(codecs, TrackType.VIDEO) +
                Writer(sink, TrackType.VIDEO)
}

private fun VideoPipeline(
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

private fun AudioPipeline(
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