@file:Suppress("MagicNumber", "UnusedPrivateMember")

package com.otaliastudios.transcoder.internal.thumbnails

import android.media.MediaFormat
import com.otaliastudios.transcoder.common.TrackStatus
import com.otaliastudios.transcoder.common.TrackType
import com.otaliastudios.transcoder.internal.CustomSegments
import com.otaliastudios.transcoder.internal.DataSources
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
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

    private val segments = CustomSegments(dataSources, tracks, ::createPipeline)

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
            return request.sourceId() + ":" + positionUs.toString()
        }
    }

    private val stubs = ArrayDeque<Stub>()

    private inner class IgnoringEosDataSource(
        private val source: DataSource,
    ) : DataSource by source {

        override fun requestKeyFrameTimestamps() = source.requestKeyFrameTimestamps()

        override fun getKeyFrameTimestamps() = source.keyFrameTimestamps

        override fun getSeekThreshold() = source.seekThreshold

        override fun mediaId() = source.mediaId()

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
        if(VERBOSE) {
            log.i("Creating pipeline #$index. absoluteUs=${stubs.joinToString { it.toString() }}")
        }
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
                        val callbackStatus = progress.trySend(thumbnail)
                        if (VERBOSE) {
                            log.i("Callback Send Status ${callbackStatus.isSuccess}")
                        }
                    }
                }
        }
    }

    private val progress = Channel<Thumbnail>(Channel.BUFFERED)


    private fun DataSource.lastKeyFrame() = keyFrameAt(keyFrameTimestamps.size - 1)

    override val progressFlow: Flow<Thumbnail> = progress.receiveAsFlow()

    private inline fun DataSource.keyFrameAt(index: Int, defaultValue: ((Int)-> Long) = {_ -> -1}) =
        keyFrameTimestamps.getOrElse(index, defaultValue)

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

    override fun addDataSource(dataSource: DataSource) {
        if (dataSources.getVideoSources().find { it.mediaId() == dataSource.mediaId() } != null) {
            return // dataSource already exists
        }
        dataSources.addVideoDataSource(dataSource)
        tracks.updateTracksInfo()
        if (tracks.active.has(TrackType.VIDEO)) {
            dataSource.selectTrack(TrackType.VIDEO)
        }
    }

    override fun removeDataSource(dataSourceId: String) {
        segments.releaseSegment(dataSourceId)
        dataSources.removeVideoDataSource(dataSourceId)
        tracks.updateTracksInfo()
    }

    override suspend fun queueThumbnails(list: List<ThumbnailRequest>) {

        val map = list.groupBy { it.sourceId() }

        map.forEach { entry ->
            val positions = entry.value.flatMap { request ->
                val duration = timer.totalDurationUs
                request.locate(duration).map { it to request }
            }.sortedBy { it.first }
            val index = dataSources[TrackType.VIDEO].indexOfFirst { it.mediaId() == entry.key }
            if (index >= 0) {
                stubs.addAll(
                    positions.map { (positionUs, request) ->
                        Stub(request, positionUs, positionUs)
                    }.toMutableList().reorder(dataSources[TrackType.VIDEO][index])
                )
            }
            if (VERBOSE) {
                log.i("Updating pipeline positions for segment Index#$index absoluteUs=${positions.joinToString { it.first.toString() }}, and stubs $stubs")
            }
        }

        if (stubs.isNotEmpty()) {
            while (currentCoroutineContext().isActive) {
                val segment =
                    stubs.firstOrNull()?.request?.sourceId()?.let { segments.getSegment(it) }
                if (VERBOSE) {
                    log.i("loop advancing for $segment")
                }
                val advanced = segment?.advance() ?: false
                // avoid calling hasNext if we advanced.
                val completed = !advanced && !segments.hasNext()
                if (completed || stubs.isEmpty()) {
                    log.i("loop broken $stubs")
                    break
                } else if (!advanced) {
                    delay(WAIT_MS)
                }
            }
        }
    }

    fun finish() {
        this.finish = true
        segments.release()
    }

    override suspend fun removePosition(source: String, positionUs: Long) {
        if (positionUs < 0) {
            stubs.removeAll{
                it.request.sourceId() == source
            }
        }
        if (stubs.firstOrNull()?.request?.sourceId() == source && positionUs == stubs.firstOrNull()?.positionUs) {
            return
        }
        val locatedTimestampUs = SingleThumbnailRequest(positionUs).locate(timer.durationUs.video)[0]
        val stub = stubs.find {it.request.sourceId() == source &&  it.positionUs == locatedTimestampUs }
        if (stub != null) {
            log.i("removePosition Match: $positionUs :$stubs")
            stubs.remove(stub)
            shouldSeek = true
        }
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
        runCatching { stubs.clear() }
        runCatching { segments.release() }
        runCatching { dataSources.release() }
    }

    companion object {
        private const val WAIT_MS = 5L
        private const val VERBOSE = false
    }
}
