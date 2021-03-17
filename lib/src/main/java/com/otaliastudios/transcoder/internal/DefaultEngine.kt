package com.otaliastudios.transcoder.internal

import android.media.MediaFormat
import com.otaliastudios.transcoder.Transcoder
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
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
import com.otaliastudios.transcoder.transcode.*
import com.otaliastudios.transcoder.validator.Validator

internal class DefaultEngine(
        private val dataSources: DataSources,
        private val dataSink: DataSink,
        strategies: TrackMap<TrackStrategy>,
        private val validator: Validator,
        private val videoRotation: Int,
        private val audioStretcher: AudioStretcher,
        private val audioResampler: AudioResampler,
        interpolator: TimeInterpolator
) : Engine() {

    private val log = Logger("DefaultEngine")

    private val tracks = Tracks(strategies, dataSources)

    private val segments = Segments(dataSources, tracks, ::createPipeline)

    private val timer = Timer(interpolator, dataSources, tracks, segments.currentIndex)

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
            type: TrackType,
            index: Int,
            status: TrackStatus,
            outputFormat: MediaFormat
    ): Pipeline {
        LOG.w("createPipeline($type, $index, $status), format=$outputFormat")
        val interpolator = timer.interpolator(type, index)
        val sources = dataSources[type]
        val source = sources[index].forcingEos {
            // Enforce EOS if we exceed duration of other tracks,
            // with a little tolerance.
            timer.readUs[type] > timer.durationUs + 100L
        }
        val sink = dataSink.ignoringEos { index < sources.lastIndex }
        return when (status) {
            TrackStatus.ABSENT -> EmptyPipeline()
            TrackStatus.REMOVING -> EmptyPipeline()
            TrackStatus.PASS_THROUGH -> PassThroughPipeline(type, source, sink, interpolator)
            TrackStatus.COMPRESSING -> RegularPipeline(type,
                    source, sink, interpolator, outputFormat,
                    videoRotation, audioStretcher, audioResampler)
        }
    }

    override fun validate(): Boolean {
        // If we have to apply some rotation, and the video should be transcoded,
        // ignore any Validator trying to abort the operation. The operation must happen
        // because we must apply the rotation.
        val ignoreValidatorResult = tracks.active.hasVideo && videoRotation != 0
        if (!validator.validate(tracks.all.video, tracks.all.audio) && !ignoreValidatorResult) {
            LOG.i("Validator has decided that the input is fine and transcoding is not necessary.")
            return false
        }
        return true
    }

    /**
     * Retrieve next segment from [Segments] and call [Segment.advance] for each track.
     * We don't have to worry about which tracks are available and how. The [Segments] class
     * will simply return null if there's nothing to be done.
     */
    override fun transcode(progress: (Double) -> Unit) {
        var loop = 0L
        LOG.i("transcode(): about to start, " +
                "durationUs=${timer.durationUs}, " +
                "audioUs=${timer.totalUs.audioOrNull()}, " +
                "videoUs=${timer.totalUs.videoOrNull()}"
        )
        while (true) {
            val advanced =
                    (segments.next(TrackType.AUDIO)?.advance() ?: false) or
                    (segments.next(TrackType.VIDEO)?.advance() ?: false)
            val completed = !advanced && !segments.hasNext() // avoid calling hasNext if we advanced.

            LOG.v("transcode(): executed step=$loop advanced=$advanced completed=$completed")
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
                LOG.v("transcode(): got progress, video=$videoProgress audio=$audioProgress")
                progress((videoProgress + audioProgress) / tracks.active.size)
            }
        }
        dataSink.stop()
    }

    override fun cleanup() {
        runCatching { segments.release() }
        runCatching { dataSink.release() }
        runCatching { dataSources.release() }
    }


    companion object {
        private val LOG = Logger("Engine")
        private val WAIT_MS = 10L
        private val PROGRESS_LOOPS = 10L
    }
}