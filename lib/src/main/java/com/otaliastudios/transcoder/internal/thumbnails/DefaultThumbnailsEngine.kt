@file:Suppress("MagicNumber", "UnusedPrivateMember")

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
import com.otaliastudios.transcoder.source.DataSource
import com.otaliastudios.transcoder.strategy.DefaultVideoStrategy
import com.otaliastudios.transcoder.strategy.RemoveTrackStrategy
import com.otaliastudios.transcoder.thumbnail.SingleThumbnailRequest
import com.otaliastudios.transcoder.thumbnail.Thumbnail
import com.otaliastudios.transcoder.thumbnail.ThumbnailRequest
import com.otaliastudios.transcoder.time.DefaultTimeInterpolator
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.ArrayList

class DefaultThumbnailsEngine(
    private val dataSources: DataSources,
    private val rotation: Int,
    resizer: Resizer
) : ThumbnailsEngine() {

    private var shouldSeek = true
    private var shouldFlush = false
    private var finish = false
    private val log = Logger("ThumbnailsEngine")
    private var previousSnapshotUs = 0L
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

    private inner class IgnoringEosDataSource(
        private val source: DataSource,
    ) : DataSource by source {
        override fun requestKeyFrameTimestamps(): Long {
            return source.requestKeyFrameTimestamps()
        }
        override fun getKeyFrameTimestamps(): ArrayList<Long> {
            return source.keyFrameTimestamps
        }

        override fun getSeekThreshold() = source.seekThreshold

        override fun isDrained(): Boolean {
            if (source.isDrained) {
                source.seekTo(stubs.firstOrNull()?.positionUs ?: -1)
            }
            return source.isDrained
        }
    }
    private fun DataSource.ignoringEOS(): DataSource = IgnoringEosDataSource(this)

    private fun createPipeline(
        type: TrackType,
        index: Int,
        status: TrackStatus,
        outputFormat: MediaFormat
    ): Pipeline {
        val source = dataSources[type][index].ignoringEOS()

        return Pipeline.build("Thumbnails") {
            Seeker(source) {
                var seek = false
                val requested = stubs.firstOrNull()?.positionUs ?: -1

                if (!shouldSeek || requested == -1L)
                    return@Seeker Pair(requested, seek)

                val seekUs: Long
                val current = source.positionUs
                val threshold = stubs.firstOrNull()?.request?.threshold() ?: 0L
                val nextKeyFrameIndex = source.search(requested)

                val nextKeyFrameUs = source.keyFrameAt(nextKeyFrameIndex) { Long.MAX_VALUE }
                val previousKeyFrameUs = source.keyFrameAt(nextKeyFrameIndex - 1) { source.lastKeyFrame() }


                val rightGap = nextKeyFrameUs - requested
                val nextKeyFrameInThreshold = rightGap <= threshold
                seek = nextKeyFrameInThreshold || previousKeyFrameUs > current || (current - requested > threshold)
                seekUs =
                    (if (nextKeyFrameInThreshold) nextKeyFrameUs else previousKeyFrameUs) + source.seekThreshold

                if (VERBOSE) {
                    log.i(
                        "seek: current ${source.positionUs}," +
                                " requested $requested, threshold $threshold, nextKeyFrameUs $nextKeyFrameUs," +
                                " nextKeyFrameInThreshold:$nextKeyFrameInThreshold, seekUs: $seekUs, flushing : $seek"
                    )
                }

                shouldFlush = seek
                shouldSeek = false
                Pair(seekUs, seek)
            } +
                Reader(source, type) +
                Decoder(source.getTrackFormat(type)!!, continuous = false) {
                    shouldFlush.also {
                        shouldFlush = false
                    }
                } +
                VideoRenderer(source.orientation, rotation, outputFormat, flipY = true, true) {
                    stubs.firstOrNull()?.positionUs ?: -1
                } +
                VideoSnapshots(outputFormat, fetchPosition) { pos, bitmap ->
                    val stub = stubs.removeFirstOrNull()
                    if (stub != null) {
                        shouldSeek = true
                        stub.actualLocalizedUs = pos
                        previousSnapshotUs = pos
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
    }

    private lateinit var progress: (Thumbnail) -> Unit

    private fun DataSource.lastKeyFrame(): Long {
        return keyFrameAt(keyFrameTimestamps.size - 1)
    }

    private inline fun DataSource.keyFrameAt(index: Int, defaultValue: ((Int)-> Long) = {_ -> -1}): Long {
        return keyFrameTimestamps.getOrElse(index, defaultValue)
    }

    private fun DataSource.search(timestampUs: Long): Int {
        if (keyFrameTimestamps.isEmpty())
            requestKeyFrameTimestamps()

        val searchIndex = keyFrameTimestamps.binarySearch(timestampUs)

        val nextKeyFrameIndex = when {
            searchIndex >= 0 -> searchIndex
            searchIndex < 0 -> {
                val index = -searchIndex - 1
                when {
                    index >= keyFrameTimestamps.size -> {
                        val ts = requestKeyFrameTimestamps()
                        if (ts == -1L) {
                            -1
                        } else {
                            search(timestampUs)
                        }
                    }
                    index < keyFrameTimestamps.size -> index
                    else -> {
                        -1 // will never reach here. kotlin is stupid
                    }
                }
            }
            else -> {
                -1 // will never reach here. kotlin is stupid
            }
        }

        return nextKeyFrameIndex
    }

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
        val locatedTimestampUs = SingleThumbnailRequest(positionUs).locate(timer.durationUs.video)[0]
        val stub = stubs.find { it.positionUs == locatedTimestampUs }
        if (stub != null) {
            log.i("removePosition Match: $positionUs :$stubs")
            stubs.remove(stub)
            shouldSeek = true
        }
    }

    private fun updatePositions(requests: List<ThumbnailRequest>, index: Int) {
        val positions = requests.flatMap { request ->
            val duration = timer.totalDurationUs
            request.locate(duration).map { it to request }
        }
        log.i("Creating pipeline #$index. absoluteUs=${positions.joinToString { it.first.toString() }}")

        stubs.addAll(
            positions.mapNotNull { (positionUs, request) ->
                val localizedUs = timer.localize(TrackType.VIDEO, index, positionUs)
                localizedUs?.let { Stub(request, positionUs, localizedUs) }
//            }.toMutableList().sortedBy { it.positionUs }
            }.toMutableList().reorder(dataSources[TrackType.VIDEO][0])
        )
    }

    private fun  List<Stub>.reorder(source: DataSource): Collection<Stub> {
        val bucketListMap = LinkedHashMap<Long, ArrayList<Stub>>()
        val finalList = ArrayList<Stub>()

        forEach {
            val nextKeyFrameIndex = source.search(it.positionUs)
            val previousKeyFrameUs = source.keyFrameAt(nextKeyFrameIndex - 1) { source.lastKeyFrame() }

            val list = bucketListMap.getOrPut(previousKeyFrameUs) { ArrayList<Stub>() }
            list.add(it)
        }
        bucketListMap.forEach {
            finalList.addAll(it.value.sortedBy { it.positionUs })
        }
        return finalList
    }

    private val fetchPosition: () -> VideoSnapshots.Request? = {
        if (stubs.isEmpty()) null
        else VideoSnapshots.Request(stubs.first().localizedUs, stubs.first().request.threshold())
    }

    override fun cleanup() {
        runCatching { segments.release() }
        runCatching { dataSources.release() }
    }

    companion object {
        private const val WAIT_MS = 5L
        private const val VERBOSE = false
    }
}
