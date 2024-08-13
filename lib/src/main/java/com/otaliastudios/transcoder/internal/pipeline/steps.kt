package com.otaliastudios.transcoder.internal.pipeline

internal abstract class BaseStep<
        Input: Any,
        InputChannel: Channel,
        Output: Any,
        OutputChannel: Channel
>(override val name: String) : Step<Input, InputChannel, Output, OutputChannel> {
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

    final override fun step(state: State.Ok<Input>, fresh: Boolean): State<Output> {
        if (fresh) {
            if (state is State.Eos) enqueueEos(state.value)
            else enqueue(state.value)
        }
        return drain()
    }
}