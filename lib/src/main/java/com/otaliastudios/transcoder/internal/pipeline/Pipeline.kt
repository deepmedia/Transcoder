package com.otaliastudios.transcoder.internal.pipeline

import com.otaliastudios.transcoder.internal.utils.Logger


private typealias AnyStep = Step<Any, Channel, Any, Channel>

internal class Pipeline private constructor(name: String, private val chain: List<AnyStep>) {

    private val log = Logger("Pipeline($name)")
    private var headState: State.Ok<Any> = State.Ok(Unit)
    private var headIndex = 0

    init {
        chain.zipWithNext().reversed().forEach { (first, next) ->
            first.initialize(next = next.channel)
        }
    }

    fun execute(): State<Unit> {
        log.w("execute(): starting. head=$headIndex steps=${chain.size} remaining=${chain.size - headIndex}")
        val head = headIndex
        var state = headState
        chain.forEachIndexed { index, step ->
            if (index < head) return@forEachIndexed
            val fresh = head == 0 || index != head
            state = executeStep(state, step, fresh) ?: run {
                log.v("execute(): step ${step.name} (#$index/${chain.size}) is waiting. headState=$headState headIndex=$headIndex")
                return State.Wait
            }
            log.v("execute(): executed ${step.name} (#$index/${chain.size}). result=$state")
            if (state is State.Eos) {
                headState = state
                headIndex = index + 1
            }
        }
        return when {
            chain.isEmpty() -> State.Eos(Unit)
            state is State.Eos -> State.Eos(Unit)
            else -> State.Ok(Unit)
        }
    }

    fun release() {
        chain.forEach { it.release() }
    }

    private fun executeStep(previous: State.Ok<Any>, step: AnyStep, fresh: Boolean): State.Ok<Any>? {
        val state = step.step(previous, fresh)
        return when (state) {
            is State.Ok -> state
            is State.Retry -> executeStep(previous, step, fresh = false)
            is State.Wait -> null
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun build(name: String, builder: () -> Builder<Unit, Channel> = { Builder() }): Pipeline {
            return Pipeline(name, builder().steps as List<AnyStep>)
        }
    }

    class Builder<D: Any, C: Channel> internal constructor(
            internal val steps: List<Step<*, *, *, *>> = listOf()
    ) {
        operator fun <NewData: Any, NewChannel: Channel> plus(
                step: Step<D, C, NewData, NewChannel>
        ): Builder<NewData, NewChannel>  = Builder(steps + step)
    }
}

internal operator fun <
        CurrData: Any, CurrChannel: Channel,
        NewData: Any, NewChannel: Channel
> Step<Unit, Channel, CurrData, CurrChannel>.plus(
        other: Step<CurrData, CurrChannel, NewData, NewChannel>
): Pipeline.Builder<NewData, NewChannel> {
    return Pipeline.Builder<CurrData, CurrChannel>(listOf(this)) + other
}