package com.otaliastudios.transcoder.internal.pipeline

internal sealed class State<out T> {

    // Running
    open class Ok<T>(val value: T) : State<T>() {
        open fun <O> map(other: O) = Ok(other)
    }

    // Run for the last time
    class Eos<T>(value: T) : Ok<T>(value) {
        override fun <O> map(other: O) = Eos(other)
    }

    // couldn't run, but might in the future
    object Wait : State<Nothing>()

    // call again as soon as possible
    object Retry : State<Nothing>()
}

interface Channel {
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

    fun step(state: State.Ok<Input>): State<Output>

    fun release() = Unit
}

internal abstract class BaseStep<
        Input: Any,
        InputChannel: Channel,
        Output: Any,
        OutputChannel: Channel
> : Step<Input, InputChannel, Output, OutputChannel> {
    protected lateinit var next: OutputChannel
    private set

    override fun initialize(next: OutputChannel) {
        this.next = next
    }
}

internal abstract class DataStep<I: Any, O: Any, C: Channel> : Step<I, C, O, C> {
    override lateinit var channel: C
    override fun initialize(next: C) {
        channel = next
    }
}