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
        interpolator: TimeInterpolator,
        private val dispatcher: Dispatcher
) : Engine() {

    private val tracks = Tracks(strategies, dataSources)

    private val segments = Segments(dataSources, tracks, ::createPipeline)

    private val timer = Timer(interpolator, dataSources, tracks, segments.currentIndex)

    init {
        dataSink.setOrientation(0) // Explicitly set 0 to output - we rotate the textures.
        val location = dataSources.all().asSequence().mapNotNull { it.location }.firstOrNull()
        if (location != null) {
            dataSink.setLocation(location[0], location[1])
        }
        dataSink.setTrackStatus(TrackType.VIDEO, tracks.all.video)
        dataSink.setTrackStatus(TrackType.AUDIO, tracks.all.audio)
    }

    private fun Throwable.isInterrupted(): Boolean {
        if (this is InterruptedException) return true
        if (this == this.cause) return false
        return this.cause?.isInterrupted() ?: false
    }

    private fun createPipeline(
            type: TrackType,
            index: Int,
            status: TrackStatus,
            outputFormat: MediaFormat
    ): Pipeline {
        val interpolator = timer.interpolator(type, index)
        val sources = dataSources[type]
        val source = sources[index].forcingEos {
            // Enforce EOS if we exceed duration of other tracks,
            // with a little tolerance.
            timer.readUs[type] > timer.durationUs + 100L
        }
        val sink = when {
            index == sources.lastIndex -> dataSink
            else -> dataSink.ignoringEos()
        }
        return when (status) {
            TrackStatus.ABSENT -> EmptyPipeline()
            TrackStatus.REMOVING -> EmptyPipeline()
            TrackStatus.PASS_THROUGH -> PassThroughPipeline(type, source, sink, interpolator)
            TrackStatus.COMPRESSING -> RegularPipeline(type,
                    source, sink, interpolator, outputFormat, videoRotation)
        }
    }

    override fun transcode() {
        if (!validate()) {
            dispatcher.dispatchSuccess(Transcoder.SUCCESS_NOT_NEEDED)
        } else try {
            LOG.v("About to transcode. Duration (us): " + timer.durationUs)
            execute {
                dispatcher.dispatchProgress(it)
            }
            dispatcher.dispatchSuccess(Transcoder.SUCCESS_TRANSCODED)
        } catch (e: Throwable) {
            if (e.isInterrupted()) {
                LOG.i("Transcode canceled.", e)
                dispatcher.dispatchCancel()
            } else {
                LOG.e("Unexpected error while transcoding.", e)
                dispatcher.dispatchFailure(e)
                throw e
            }
        } finally {
            cleanup()
        }
    }

    private fun validate(): Boolean {
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
    private fun execute(progress: (Double) -> Unit) {
        var loop = 0L
        while (true) {
            LOG.v("new step: $loop")
            val advanced =
                    (segments.next(TrackType.AUDIO)?.advance() ?: false) or
                    (segments.next(TrackType.VIDEO)?.advance() ?: false)
            val completed = !advanced && !segments.hasNext() // avoid calling hasNext if we advanced.

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
                LOG.v("progress - video:$videoProgress audio:$audioProgress")
                progress((videoProgress + audioProgress) / tracks.active.size)
            }
        }
        dataSink.stop()
    }

    private fun cleanup() {
        runCatching { segments.release() }
        runCatching { dataSink.release() }
    }


    companion object {
        private val LOG = Logger("Engine")
        private val WAIT_MS = 10L
        private val PROGRESS_LOOPS = 10L
    }
}