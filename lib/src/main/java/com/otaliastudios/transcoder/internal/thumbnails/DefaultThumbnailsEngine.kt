package com.otaliastudios.transcoder.internal.thumbnails

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.DataSources
import com.otaliastudios.transcoder.internal.Segments
import com.otaliastudios.transcoder.internal.Timer
import com.otaliastudios.transcoder.internal.Tracks
import com.otaliastudios.transcoder.internal.codec.Decoder
import com.otaliastudios.transcoder.internal.data.Reader
import com.otaliastudios.transcoder.internal.data.Seeker
import com.otaliastudios.transcoder.internal.pipeline.Pipeline
import com.otaliastudios.transcoder.internal.pipeline.plus
import com.otaliastudios.transcoder.internal.utils.Logger
import com.otaliastudios.transcoder.internal.utils.forcingEos
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import com.otaliastudios.transcoder.internal.video.VideoRenderer
import com.otaliastudios.transcoder.internal.video.VideoSnapshots
import com.otaliastudios.transcoder.resize.Resizer
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest
import com.otaliastudios.transcoder.time.DefaultTimeInterpolator

class DefaultThumbnailsEngine(
    private val dataSources: DataSources,
    private val rotation: Int,
    resizer: Resizer,
    requests: List<ThumbnailRequest>
) : ThumbnailsEngine() {

    private var finish = false
    private val log = Logger("ThumbnailsEngine")

    // Huge framerate triks the VideoRenderer into not dropping frames, which is important
    // for thumbnail requests that want to catch the very last frame.
    private val tracks = Tracks(
        trackMapOf(
            video = DefaultVideoStrategy.Builder()
                .frameRate(120)
                .addResizer(resizer)
                .build(),
            audio = RemoveTrackStrategy()
        ), dataSources, rotation, true
    )

    private val segments = Segments(dataSources, tracks, ::createPipeline)

    private val timer = Timer(DefaultTimeInterpolator(), dataSources, tracks, segments.currentIndex)

    init {
        log.i("Created Tracks, Segments, Timer...")
    }

    private class Stub(
        val request: ThumbnailRequest,
        val positionUs: Long,
        val localizedUs: Long
    ) {
        var actualLocalizedUs: Long = localizedUs
    }

    private var stubs = mutableListOf<Stub>()
    private lateinit var type: TrackType
    private var index: Int = 0
    private fun createPipeline(
        type: TrackType,
        index: Int,
        status: TrackStatus,
        outputFormat: MediaFormat
    ): Pipeline {
        this.type = type
        this.index = index

//        if (stubs.isEmpty()) return EmptyPipeline()
        val source = dataSources[type][index].forcingEos {
            stubs.isEmpty() || finish
        }
        return Pipeline.build("Thumbnails") {
            Seeker(source, fetchPositions) { it == stubs.firstOrNull()?.localizedUs } +
                    Reader(source, type) +
                    Decoder(source.getTrackFormat(type)!!, continuous = false) +
                    VideoRenderer(source.orientation, rotation, outputFormat, flipY = true) +
                    VideoSnapshots(outputFormat, fetchPositions, 1000 * 1000) { pos, bitmap ->
                        val stub = stubs.removeFirst()
                        timestamps.removeFirst()
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

    override fun queueThumbnails(list: List<ThumbnailRequest>, progress: (Thumbnail) -> Unit) {
        segments.next(TrackType.VIDEO)
        this.updatePositions(list)
        this.progress = progress
        while (true) {
            val advanced = segments.next(TrackType.VIDEO)?.advance() ?: false
            val completed =
                !advanced && !segments.hasNext() // avoid calling hasNext if we advanced.
            if (Thread.interrupted()) {
                throw InterruptedException()
            } else if (completed) {
                break
            } else if (!advanced) {
                Thread.sleep(WAIT_MS)
            }
        }
    }

    fun finish() {
        this.finish = true
    }

    private fun updatePositions(requests: List<ThumbnailRequest>) {
        val positions = requests.flatMap { request ->
            val duration = timer.totalDurationUs
            request.locate(duration).map { it to request }
        }.sortedBy { it.first }
        log.i("Creating pipeline #$index. absoluteUs=${positions.joinToString { it.first.toString() }}")

        stubs = positions.mapNotNull { (positionUs, request) ->
            val localizedUs = timer.localize(type, index, positionUs)
            localizedUs?.let { Stub(request, positionUs, localizedUs) }
        }.toMutableList()

        timestamps = stubs.map { it.localizedUs } as MutableList<Long>

    }

    private var timestamps = mutableListOf<Long>()
    private val fetchPositions: () -> List<Long> = {
        timestamps
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