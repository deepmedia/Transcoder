package com.otaliastudios.transcoder.internal.transcode

import android.content.Context
import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.Codecs
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.Segment
import com.otaliastudios.transcoder.internal.Segments
import com.otaliastudios.transcoder.internal.Timer
import com.otaliastudios.transcoder.internal.Tracks
import com.otaliastudios.transcoder.internal.pipeline.EmptyPipeline
import com.otaliastudios.transcoder.internal.pipeline.PassThroughPipeline
import com.otaliastudios.transcoder.internal.pipeline.Pipeline
import com.otaliastudios.transcoder.internal.pipeline.RegularPipeline
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.TrackMap
import com.otaliastudios.transcoder.internal.utils.forcingEos
import com.otaliastudios.transcoder.internal.utils.ignoringEos
import com.otaliastudios.transcoder.resample.AudioResampler
import com.otaliastudios.transcoder.sink.DataSink
import com.otaliastudios.transcoder.strategy.TrackStrategy
import com.otaliastudios.transcoder.stretch.AudioStretcher
import com.otaliastudios.transcoder.time.TimeInterpolator
import com.otaliastudios.transcoder.validator.Validator

class DefaultTranscodeEngine(
        private val dataSources: DataSources,
        private val dataSink: DataSink,
        strategies: TrackMap<TrackStrategy>,
        private val validator: Validator,
        private val videoRotation: Int,
        private val audioStretcher: AudioStretcher,
        private val audioResampler: AudioResampler,
        interpolator: TimeInterpolator
) : TranscodeEngine() {

    private val log = Logger("TranscodeEngine")

    private val tracks = Tracks(strategies, dataSources, videoRotation, false)

    private val segments = Segments(dataSources, tracks, ::createPipeline)

    private val timer = Timer(interpolator, dataSources, tracks, segments.currentIndex)

    private val codecs = Codecs(dataSources, tracks, segments.currentIndex)

    init {
        log.i("Created Tracks, Segments, Timer...")
    }

    init {
        dataSink.setOrientation(0) // Explicitly set 0 to output - we rotate the textures.
        val location = dataSources.all().asSequence().mapNotNull { it.location }.firstOrNull()
        if (location != null) {
            dataSink.setLocation(location[0], location[1])
        }
        dataSink.setTrackStatus(TrackType.VIDEO, tracks.all.video)
        dataSink.setTrackStatus(TrackType.AUDIO, tracks.all.audio)
        log.i("Set up the DataSink...")
    }

    private fun createPipeline(
        context: Context,
        type: TrackType,
        index: Int,
        status: TrackStatus,
        outputFormat: MediaFormat
    ): Pipeline {
        log.w("createPipeline($type, $index, $status), format=$outputFormat")
        val interpolator = timer.interpolator(type, index)
        val sources = dataSources[type]
        val source = sources[index].forcingEos {
            // Enforce EOS if we exceed duration of other tracks,
            // with a little tolerance.
            timer.positionUs[type] > timer.totalDurationUs + 100L
        }
        val sink = dataSink.ignoringEos { index < sources.lastIndex }
        return when (status) {
            TrackStatus.ABSENT -> EmptyPipeline()
            TrackStatus.REMOVING -> EmptyPipeline()
            TrackStatus.PASS_THROUGH -> PassThroughPipeline(type, source, sink, interpolator)
            TrackStatus.COMPRESSING -> RegularPipeline(context,type,
                    source, sink, interpolator, outputFormat, codecs,
                    videoRotation, audioStretcher, audioResampler)
        }
    }

    override fun validate(): Boolean {
        if (!validator.validate(tracks.all.video, tracks.all.audio)) {
            log.i("Validator has decided that the input is fine and transcoding is not necessary.")
            return false
        }
        return true
    }

    /**
     * Retrieve next segment from [Segments] and call [Segment.advance] for each track.
     * We don't have to worry about which tracks are available and how. The [Segments] class
     * will simply return null if there's nothing to be done.
     */
    override fun transcode(context: Context, progress: (Double) -> Unit) {
        var loop = 0L
        log.i("transcode(): about to start, " +
                "durationUs=${timer.totalDurationUs}, " +
                "audioUs=${timer.durationUs.audioOrNull()}, " +
                "videoUs=${timer.durationUs.videoOrNull()}"
        )
        while (true) {
            // Create both segments before reading. Creating the segment calls source.selectTrack,
            // and if source is the same, it's important that both tracks are selected before
            // reading (or even worse, seeking. DataSource.seek is broken if you add a track later on).
            val audio = segments.next(context,TrackType.AUDIO)
            val video = segments.next(context,TrackType.VIDEO)
            val advanced = (audio?.advance() ?: false) or (video?.advance() ?: false)
            val completed = !advanced && !segments.hasNext() // avoid calling hasNext if we advanced.

            log.v("transcode(): executed step=$loop advanced=$advanced completed=$completed")
            if (Thread.interrupted()) {
                throw InterruptedException()
            } else if (completed) {
                progress(1.0)
                break
            } else if (!advanced) {
                Thread.sleep(WAIT_MS)
            } else if (++loop % PROGRESS_LOOPS == 0L) {
                val audioProgress = timer.progress.audio
                val videoProgress = timer.progress.video
                log.v("transcode(): got progress, video=$videoProgress audio=$audioProgress")
                progress((videoProgress + audioProgress) / tracks.active.size)
            }
        }
        dataSink.stop()
    }

    override fun cleanup() {
        runCatching { segments.release() }
        runCatching { dataSink.release() }
        runCatching { dataSources.release() }
        runCatching { codecs.release() }
    }


    companion object {
        private val WAIT_MS = 10L
        private val PROGRESS_LOOPS = 10L
    }
}