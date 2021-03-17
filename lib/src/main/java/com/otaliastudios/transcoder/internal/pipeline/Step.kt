package com.otaliastudios.transcoder.internal.pipeline

internal interface Channel {
    companion object : Channel
}

internal interface Step<
        Input: Any,
        InputChannel: Channel,
        Output: Any,
        OutputChannel: Channel
> {
    val channel: InputChannel

    fun initialize(next: OutputChannel) = Unit

    fun step(state: State.Ok<Input>, fresh: Boolean): State<Output>

    fun release() = Unit
}

internal val Step<*, *, *, *>.name get() = this::class.simpleName