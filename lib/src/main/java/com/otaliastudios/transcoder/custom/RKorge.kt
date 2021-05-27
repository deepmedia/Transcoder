package com.otaliastudios.transcoder.custom

import android.content.Context
import android.graphics.Point
import com.otaliastudios.transcoder.internal.pipeline.Channel
import com.otaliastudios.transcoder.internal.pipeline.State
import com.otaliastudios.transcoder.internal.pipeline.Step
import io.invideo.renderer.IVRootGameMainAndroid
import io.invideo.renderer.RenderTreeEvent
import io.invideo.renderer.RendererConfiguration
import io.invideo.renderer.SceneTimeUpdateEvent
import io.invideo.renderer.UpdateResourceEvent
import io.invideo.renderer.VideoPlayerFactoryInterface
import io.invideo.renderer.VideoSeekEvent
import io.invideo.renderer.extensions.dispatchRenderTreeEvent
import io.invideo.renderer.extensions.dispatchSceneTimeUpdateEvent
import io.invideo.renderer.extensions.dispatchUpdateResourcesEvent
import io.invideo.renderer.extensions.dispatchVideoSeekEvent
import io.invideo.renderer.getTimeline
import io.invideo.renderer.rendertree.Node
import io.invideo.renderer.rendertree.RenderTree
import io.invideo.renderer.timeline.IVEventDispatcher
import io.invideo.renderer.timeline.TimeLinePlayer
import io.invideo.renderer.timeline.VideoEventDispatcher
import kotlin.time.ExperimentalTime

data class RenderingData(
    val timeMsCurrent: Long,
    val timeMsPrevious: Long,
    val updateTime: () -> Unit
)

interface RenderingChannel : Channel {
    fun setDimensions(point: Point)
}

@OptIn(ExperimentalTime::class)
class RKorge(
    context: Context,
    config: RendererConfiguration,
    videoPlayerFactoryInterface: VideoPlayerFactoryInterface?
) : Step<RenderingData, RenderingChannel, Long, Channel>, RenderingChannel {
    private var isInitialized = false
    var reshape = true

    val rootGameMain = IVRootGameMainAndroid(context, config, videoPlayerFactoryInterface)
    private var oldBoundSize = Point(0, 0)

    private val timelinePlayer: TimeLinePlayer
    private val sceneTimeUpdateEvent = SceneTimeUpdateEvent(timeInSeconds = 0.0)
    lateinit var size: Point
    override val channel = this

    init {
        timelinePlayer = TimeLinePlayer(getTimeline(), EventDispatcher())
    }

    override fun step(state: State.Ok<RenderingData>, fresh: Boolean): State<Long> {
        val (timeMsCurrent, timeMsPrevious) = state.value

        return if (state is State.Eos) {
            State.Eos(0L)
        } else {
            sceneTimeUpdateEvent.timeInSeconds = timeMsCurrent / 1000.0
            rootGameMain.gameWindow.dispatchSceneTimeUpdateEvent(sceneTimeUpdateEvent)

            timelinePlayer.checkAndUpdate(timeMsPrevious / 1000.0, timeMsCurrent / 1000.0, false)
            render(size, 1.0f)
            state.value.updateTime()
            State.Ok(state.value.timeMsCurrent * 1000L)
        }

    }

    override fun setDimensions(point: Point) {
        size = point
    }

    fun render(boundSize: Point, contentScaleFactor: Float = 1.0f) {
        if (!isInitialized) {
            isInitialized = true
            rootGameMain.preRunMain()
            rootGameMain.gameWindow.dispatchInitEvent()
            rootGameMain.runMain()
            reshape = true
        }

        if (!reshape && oldBoundSize.equals(boundSize.x, boundSize.y)) {
            reshape = true
        }
        oldBoundSize = boundSize

        if (reshape) {
            reshape = false
            val width = (boundSize.x * contentScaleFactor).toInt()
            val height = (boundSize.y * contentScaleFactor).toInt()
            rootGameMain.gameWindow.dispatchReshapeEvent(0, 0, width, height)
        }

        rootGameMain.gameWindow.frame()
    }

    inner class EventDispatcher : IVEventDispatcher {
        override fun updateResources(node: Node, shouldLoad: Boolean) {
            val event = UpdateResourceEvent(node, shouldLoad)
            rootGameMain.gameWindow.dispatchUpdateResourcesEvent(event)
        }

        override fun updateRenderTree(renderTree: RenderTree) {
            val event = RenderTreeEvent(renderTree)
            rootGameMain.gameWindow.dispatchRenderTreeEvent(event)
        }

        override fun play() {
        }

        override fun pause() {
        }

        override fun seek(seekTimeMs: Long) {
            rootGameMain.gameWindow.dispatchVideoSeekEvent(VideoSeekEvent(seekTimeMs))
        }
    }
}
