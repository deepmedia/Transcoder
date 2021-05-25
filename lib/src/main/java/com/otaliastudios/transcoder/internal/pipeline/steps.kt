package com.otaliastudios.transcoder.internal.pipeline

abstract class BaseStep<
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

abstract class DataStep<D: Any, C: Channel> : Step<D, C, D, C> {
    override lateinit var channel: C
    override fun initialize(next: C) {
        channel = next
    }
}

abstract class QueuedStep<
        Input: Any,
        InputChannel: Channel,
        Output: Any,
        OutputChannel: Channel
> : BaseStep<Input, InputChannel, Output, OutputChannel>() {

    protected abstract fun enqueue(data: Input)

    protected abstract fun enqueueEos(data: Input)

    protected abstract fun drain(): State<Output>

    final override fun step(state: State.Ok<Input>, fresh: Boolean): State<Output> {
        if (fresh) {
            if (state is State.Eos) enqueueEos(state.value)
            else enqueue(state.value)
        }
        return drain()
    }
}