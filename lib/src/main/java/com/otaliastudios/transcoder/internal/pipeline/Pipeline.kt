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

    // Returns Eos, Ok or Wait
    fun execute(): State<Unit> {
        log.v("execute(): starting. head=$headIndex steps=${chain.size} remaining=${chain.size - headIndex}")
        val head = headIndex
        var state = headState
        chain.forEachIndexed { index, step ->
            if (index < head) return@forEachIndexed
            val fresh = head == 0 || index != head

            fun executeStep(fresh: Boolean): State.Wait<Any>? {
                return when (val newState = step.step(state, fresh)) {
                    is State.Eos -> {
                        state = newState
                        log.i("execute(): EOS from ${step.name} (#$index/${chain.size}).")
                        headState = newState
                        headIndex = index + 1
                        null
                    }
                    is State.Ok -> {
                        state = newState
                        null
                    }
                    is State.Retry -> executeStep(fresh = false)
                    is State.Wait -> return newState
                }
            }

            val wait = executeStep(fresh)
            if (wait != null) return State.Wait(wait.sleep)
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

    companion object {
        @Suppress("UNCHECKED_CAST")
        internal fun build(name: String, builder: () -> Builder<*, Channel> = { Builder<Unit, Channel>() }): Pipeline {
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
