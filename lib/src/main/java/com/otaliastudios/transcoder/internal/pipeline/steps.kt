package com.otaliastudios.transcoder.internal.pipeline

import com.otaliastudios.transcoder.internal.utils.Logger

internal abstract class BaseStep<
    Input: Any,
    InputChannel: Channel,
    Output: Any,
    OutputChannel: Channel
>(final override val name: String) : Step<Input, InputChannel, Output, OutputChannel> {

    protected val log = Logger(name)

    protected lateinit var next: OutputChannel
    private set

    override fun initialize(next: OutputChannel) {
        this.next = next
    }
}

internal abstract class TransformStep<D: Any, C: Channel>(name: String) : BaseStep<D, C, D, C>(name) {
    override lateinit var channel: C
    override fun initialize(next: C) {
        super.initialize(next)
        channel = next
    }
}

internal abstract class QueuedStep<
    Input: Any,
    InputChannel: Channel,
    Output: Any,
    OutputChannel: Channel
>(name: String) : BaseStep<Input, InputChannel, Output, OutputChannel>(name) {

    protected abstract fun enqueue(data: Input)

    protected abstract fun enqueueEos(data: Input)

    protected abstract fun drain(): State<Output>

    final override fun advance(state: State.Ok<Input>): State<Output> {
        if (state is State.Eos) enqueueEos(state.value)
        else enqueue(state.value)
        return drain()
    }

    fun retry(): State<Output> {
        return drain()
    }
}