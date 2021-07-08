package com.otaliastudios.transcoder.internal.pipeline

import com.otaliastudios.transcoder.internal.codec.EncoderChannel
import com.otaliastudios.transcoder.internal.codec.EncoderData
import com.otaliastudios.transcoder.internal.utils.Logger

class CustomPipeline private constructor(name: String, private val chain: List<AnyStep>) {

    private val log = Logger("Pipeline($name)")
    private var headState: State.Ok<Any> = State.Ok(Unit)
    private var headIndex = 0

    init {
        chain.zipWithNext().reversed().forEach { (first, next) ->
            first.initialize(next = next.channel)
        }
    }

    // Returns Eos, Ok or Wait
    fun execute(): State<EncoderData> {
        log.v("execute(): starting. head=$headIndex steps=${chain.size} remaining=${chain.size - headIndex}")
        val head = headIndex
        var state = headState
        chain.forEachIndexed { index, step ->
            if (index < head) return@forEachIndexed
            val fresh = head == 0 || index != head
            state = executeStep(state, step, fresh) ?: run {
                log.v("execute(): step ${step.name} (#$index/${chain.size}) is waiting. headState=$headState headIndex=$headIndex")
                return State.Wait
            }
            // log.v("execute(): executed ${step.name} (#$index/${chain.size}). result=$state")
            if (state is State.Eos) {
                log.i("execute(): EOS from ${step.name} (#$index/${chain.size}).")
                headState = state
                headIndex = index + 1
            }
        }
        return when {
            chain.isEmpty() -> State.Eos(EncoderData.Empty)
            state is State.Eos -> State.Eos(EncoderData.Empty)
            else -> State.Ok(state.value as EncoderData)
        }
    }

    fun release() {
        chain.forEach { it.release() }
    }

    private fun executeStep(previous: State.Ok<Any>, step: AnyStep, fresh: Boolean): State.Ok<Any>? {
        return when (val state = step.step(previous, fresh)) {
            is State.Ok -> state
            is State.Retry -> executeStep(previous, step, fresh = false)
            is State.Wait -> null
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
         fun build(name: String, builder: () -> Pipeline.Builder<*, EncoderChannel> = { Pipeline.Builder<EncoderData, EncoderChannel>() }): CustomPipeline {
            return CustomPipeline(name, builder().steps as List<AnyStep>)
        }
    }
}