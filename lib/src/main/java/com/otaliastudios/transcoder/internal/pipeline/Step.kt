package com.otaliastudios.transcoder.internal.pipeline

// TODO this could be Any
interface Channel {
    companion object : Channel
}

interface Step<
    Input : Any,
    InputChannel : Channel,
    Output : Any,
    OutputChannel : Channel
    > {
    val channel: InputChannel

    fun initialize(next: OutputChannel) = Unit

    fun step(state: State.Ok<Input>, fresh: Boolean): State<Output>

    fun release() = Unit
}

val Step<*, *, *, *>.name get() = this::class.simpleName
