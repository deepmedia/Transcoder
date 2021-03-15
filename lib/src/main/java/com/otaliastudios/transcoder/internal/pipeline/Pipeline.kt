package com.otaliastudios.transcoder.internal.pipeline


private typealias AnyStep = Step<Any, Channel, Any, Channel>

internal class Pipeline(
        private val chain: List<AnyStep>
) {

    init {
        chain.zipWithNext().reversed().forEach { (first, next) ->
            first.initialize(next = next.channel)
        }
    }

    private var state: State.Ok<Any> = State.Ok(Unit)
    private var index = 0

    fun execute(): State<Unit> {
        val steps = chain.subList(index, chain.size)
        var state = state
        for (step in steps) {
            state = executeStep(state, step) ?: return State.Wait
            if (state is State.Eos) {
                this.state = state
                this.index = steps.indexOf(step)
            }
        }
        // State is either Ok or Eos
        return when {
            state is State.Eos || chain.isEmpty() -> State.Eos(Unit)
            else -> State.Ok(Unit)
        }
    }

    fun release() {
        chain.forEach { it.release() }
    }

    private fun executeStep(previous: State.Ok<Any>, step: AnyStep): State.Ok<Any>? {
        val state = step.step(previous)
        return when (state) {
            is State.Ok -> state
            is State.Retry -> executeStep(previous, step)
            is State.Wait -> null
        }
    }

    class Builder<D: Any, C: Channel> private constructor(
            internal val steps: List<Step<*, *, *, *>> = listOf()
    ) {

        fun <NewData: Any, NewChannel: Channel> then(
                step: Step<D, C, NewData, NewChannel>
        ): Builder<NewData, NewChannel>  = Builder(steps + step)

        companion object {
            operator fun invoke() = Builder<Unit, Channel>()
        }
    }
}

internal fun Pipeline.Builder<Unit, Channel>.build(): Pipeline {
    @Suppress("UNCHECKED_CAST")
    return Pipeline(steps as List<AnyStep>)
}