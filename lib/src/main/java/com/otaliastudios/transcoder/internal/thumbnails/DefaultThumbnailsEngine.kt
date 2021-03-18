package com.otaliastudios.transcoder.internal.thumbnails

import android.graphics.Bitmap
import android.media.MediaFormat
import android.media.MediaFormat.KEY_HEIGHT
import android.media.MediaFormat.KEY_WIDTH
import android.opengl.GLES20
import com.otaliastudios.opengl.core.Egloo
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.Segments
import com.otaliastudios.transcoder.internal.Timer
import com.otaliastudios.transcoder.internal.Tracks
import com.otaliastudios.transcoder.internal.codec.Decoder
import com.otaliastudios.transcoder.internal.codec.EncoderChannel
import com.otaliastudios.transcoder.internal.codec.EncoderData
import com.otaliastudios.transcoder.internal.data.Reader
import com.otaliastudios.transcoder.internal.data.Seeker
import com.otaliastudios.transcoder.internal.pipeline.*
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.EmptyPipeline
import com.otaliastudios.transcoder.internal.pipeline.Pipeline
import com.otaliastudios.transcoder.internal.utils.*
import com.otaliastudios.transcoder.internal.utils.forcingEos
import com.otaliastudios.transcoder.internal.video.VideoPublisher
import com.otaliastudios.transcoder.internal.video.VideoRenderer
import com.otaliastudios.transcoder.internal.video.VideoSnapshots
import com.otaliastudios.transcoder.resize.Resizer
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest
import com.otaliastudios.transcoder.time.DefaultTimeInterpolator
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

internal class DefaultThumbnailsEngine(
        private val dataSources: DataSources,
        private val rotation: Int,
        resizer: Resizer,
        requests: List<ThumbnailRequest>
) : ThumbnailsEngine() {

    private val log = Logger("ThumbnailsEngine")

    // Huge framerate triks the VideoRenderer into not dropping frames, which is important
    // for thumbnail requests that want to catch the very last frame.
    private val tracks = Tracks(trackMapOf(
            video = DefaultVideoStrategy.Builder()
                    .frameRate(120)
                    .addResizer(resizer)
                    .build(),
            audio = RemoveTrackStrategy()
    ), dataSources, rotation, true)

    private val segments = Segments(dataSources, tracks, ::createPipeline)

    private val timer = Timer(DefaultTimeInterpolator(), dataSources, tracks, segments.currentIndex)

    init {
        log.i("Created Tracks, Segments, Timer...")
    }

    private val positions = requests.flatMap { request ->
        val duration = timer.totalDurationUs
        request.locate(duration).map { it to request }
    }.sortedBy { it.first }

    private class Stub(
            val request: ThumbnailRequest,
            val positionUs: Long,
            val localizedUs: Long) {
        var actualLocalizedUs: Long = localizedUs
    }

    private fun createPipeline(
            type: TrackType,
            index: Int,
            status: TrackStatus,
            outputFormat: MediaFormat
    ): Pipeline {
        log.i("Creating pipeline #$index. absoluteUs=${positions.joinToString { it.first.toString() }}")
        val stubs = positions.mapNotNull { (positionUs, request) ->
            val localizedUs = timer.localize(type, index, positionUs)
            localizedUs?.let { Stub(request, positionUs, localizedUs) }
        }.toMutableList()

        if (stubs.isEmpty()) return EmptyPipeline()
        val source = dataSources[type][index].forcingEos {
            stubs.isEmpty()
        }
        val positions = stubs.map { it.localizedUs }
        log.i("Requests for step #$index: ${positions.joinToString()} [duration=${source.durationUs}]")
        return Pipeline.build("Thumbnails") {
            Seeker(source, positions) { it == stubs.firstOrNull()?.localizedUs } +
                    Reader(source, type) +
                    Decoder(source.getTrackFormat(type)!!, continuous = false) +
                    VideoRenderer(source.orientation, rotation, outputFormat) +
                    VideoSnapshots(outputFormat, positions, 50 * 1000) { pos, bitmap ->
                        val stub = stubs.removeFirst()
                        stub.actualLocalizedUs = pos
                        log.i("Got snapshot. positionUs=${stub.positionUs} " +
                                "localizedUs=${stub.localizedUs} " +
                                "actualLocalizedUs=${stub.actualLocalizedUs} " +
                                "deltaUs=${stub.localizedUs - stub.actualLocalizedUs}")
                        val thumbnail = Thumbnail(stub.request, stub.positionUs, bitmap)
                        progress(thumbnail)
                    }
        }
    }

    private lateinit var progress: (Thumbnail) -> Unit

    override fun thumbnails(progress: (Thumbnail) -> Unit) {
        this.progress = progress
        while (true) {
            val advanced = segments.next(TrackType.VIDEO)?.advance() ?: false
            val completed = !advanced && !segments.hasNext() // avoid calling hasNext if we advanced.
            if (Thread.interrupted()) {
                throw InterruptedException()
            } else if (completed) {
                break
            } else if (!advanced) {
                Thread.sleep(WAIT_MS)
            }
        }
    }

    override fun cleanup() {
        runCatching { segments.release() }
        runCatching { dataSources.release() }
    }

    companion object {
        private val WAIT_MS = 10L
        private val PROGRESS_LOOPS = 10L
    }
}