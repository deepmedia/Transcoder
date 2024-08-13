package com.otaliastudios.transcoder.internal.pipeline

// TODO this could be Any
internal interface Channel {
    companion object : Channel
}

internal interface Step<
    Input: Any,
    InputChannel: Channel,
    Output: Any,
    OutputChannel: Channel
> {
    val name: String
    val channel: InputChannel

    fun initialize(next: OutputChannel) = Unit

    fun advance(state: State.Ok<Input>): State<Output>

    fun release() = Unit
}
