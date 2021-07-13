@file:Suppress("MagicNumber")

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
import com.otaliastudios.transcoder.internal.utils.trackMapOf
import com.otaliastudios.transcoder.internal.video.VideoRenderer
import com.otaliastudios.transcoder.internal.video.VideoSnapshots
import com.otaliastudios.transcoder.resize.Resizer
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest
import com.otaliastudios.transcoder.time.DefaultTimeInterpolator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class DefaultThumbnailsEngine(
    private val dataSources: DataSources,
    private val rotation: Int,
    resizer: Resizer
) : ThumbnailsEngine() {

    private var shouldSeek = true
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
        ),
        dataSources,
        rotation,
        true
    )

    private val segments = Segments(dataSources, tracks, ::createPipeline, false)

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
        override fun toString(): String {
            return positionUs.toString()
        }
    }

    private val stubs = ArrayDeque<Stub>()

    private fun createPipeline(
        type: TrackType,
        index: Int,
        status: TrackStatus,
        outputFormat: MediaFormat
    ): Pipeline {
        val source = dataSources[type][index]
        return Pipeline.build("Thumbnails") {
            Seeker(source, fetchPosition) {
                shouldSeek.also {
                    shouldSeek = false
                }
                //                log.i("Seeker state check: $it :$stubs")
            } +
                Reader(source, type) +
                Decoder(source.getTrackFormat(type)!!, continuous = false) +
                VideoRenderer(source.orientation, rotation, outputFormat, flipY = true, true) {
                    stubs.firstOrNull()?.positionUs ?: -1
                } +
                VideoSnapshots(outputFormat, fetchPosition, snapshotAccuracyUs) { pos, bitmap ->
                    shouldSeek = true
                    val stub = stubs.removeFirst()
                    stub.actualLocalizedUs = pos
                    log.i(
                        "Got snapshot. positionUs=${stub.positionUs} " +
                            "localizedUs=${stub.localizedUs} " +
                            "actualLocalizedUs=${stub.actualLocalizedUs} " +
                            "deltaUs=${stub.localizedUs - stub.actualLocalizedUs}"
                    )
                    val thumbnail = Thumbnail(stub.request, stub.positionUs, bitmap)
                    progress(thumbnail)
                }
        }
    }

    private lateinit var progress: (Thumbnail) -> Unit

    override suspend fun queueThumbnails(list: List<ThumbnailRequest>, progress: (Thumbnail) -> Unit) {
        val segment = segments.next(TrackType.VIDEO)
        segment?.let {
            this.updatePositions(list, it.index)
        }
        this.progress = progress
        while (currentCoroutineContext().isActive) {
            val advanced = segments.next(TrackType.VIDEO)?.advance() ?: false
            val completed = !advanced && !segments.hasNext() // avoid calling hasNext if we advanced.
            if (completed || stubs.isEmpty()) {
                log.i("loop broken $stubs")
                break
            } else if (!advanced) {
                delay(WAIT_MS)
            }
        }
    }

    fun finish() {
        this.finish = true
        segments.release()
    }

    override suspend fun removePosition(positionUs: Long) {
        if (positionUs == stubs.firstOrNull()?.positionUs) {
            return
        }
        val stub = stubs.find { it.positionUs == positionUs }
        if (stub != null) {
            log.i("removePosition Match: $positionUs :$stubs")
            stubs.remove(stub)
        }
    }

    private fun updatePositions(requests: List<ThumbnailRequest>, index: Int) {
        val positions = requests.flatMap { request ->
            val duration = timer.totalDurationUs
            request.locate(duration).map { it to request }
        }.sortedBy { it.first }
        log.i("Creating pipeline #$index. absoluteUs=${positions.joinToString { it.first.toString() }}")

        stubs.addAll(
            positions.mapNotNull { (positionUs, request) ->
                val localizedUs = timer.localize(TrackType.VIDEO, index, positionUs)
                localizedUs?.let { Stub(request, positionUs, localizedUs) }
            }.toMutableList()
        )
    }

    private val fetchPosition: () -> Long? = { stubs.firstOrNull()?.localizedUs }

    override fun cleanup() {
        runCatching { segments.release() }
        runCatching { dataSources.release() }
    }

    companion object {
        private const val WAIT_MS = 10L
        private const val PROGRESS_LOOPS = 10L
        private const val snapshotAccuracyUs = 500 * 1000L
    }
}
