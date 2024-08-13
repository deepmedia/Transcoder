package com.otaliastudios.transcoder.internal.pipeline

import com.otaliastudios.transcoder.internal.utils.Logger



private class PipelineItem(
    val step: Step<Any, Channel, Any, Channel>,
) {
    var success: State.Ok<Any>? = null
    var failure: State.Wait? = null

    fun set(output: State<Any>?) {
        success = output as? State.Ok
        failure = output as? State.Wait
    }
}

internal class Pipeline private constructor(name: String, private val items: List<PipelineItem>) {

    private val log = Logger("${name}Pipeline")

    init {
        items.zipWithNext().reversed().forEach { (first, next) ->
            first.step.initialize(next = next.step.channel)
        }
    }

    // Returns Eos, Ok or Wait
    fun execute(): State<Unit> {
        var headState: State.Ok<Any> = State.Ok(Unit)
        var headFresh = true
        // In case of failure in the previous run, we should re-run all items before the failed one
        // This is important for decoders/encoders that need more input before they can output
        val previouslyFailedIndex = items.indexOfLast { it.failure != null }.takeIf { it >= 0 }
        log.v("LOOP (previouslyFailed: ${previouslyFailedIndex})")
        for (i in items.indices) {
            val item = items[i]
            val cached = item.success
            if (cached != null && (!headFresh || cached is State.Eos)) {
                log.v("${i+1}/${items.size} '${item.step.name}' SKIP ${if (item.success is State.Eos) "(eos)" else "(handled)"}")
                headState = cached
                headFresh = false
                continue
            }
            // This item either:
            // - never run (cached == null, fresh = true)
            // - caused failure in the previous run (i == previouslyFailedIndex, failure != null)
            // - run (with failure or success) then one of the items following it failed (i < previouslyFailedIndex, cached != null)
            log.v("${i+1}/${items.size} '${item.step.name}' START (${if (headFresh) "fresh" else "stale"})")
            item.set(item.step.step(headState, headFresh))
            if (item.success != null) {
                log.v("${i+1}/${items.size} '${item.step.name}' SUCCESS ${if (item.success is State.Eos) "(eos)" else ""}")
                headState = item.success!!
                headFresh = true
                if (i == items.lastIndex) items.forEach {
                    if (it.success !is State.Eos) it.set(null)
                }
                continue
            }
            // Item failed. Check if we had a later failure in the previous run. In that case
            // we should retry that too. Note: `cached` should always be not null in this branch
            // but let's avoid throwing
            if (previouslyFailedIndex != null && i < previouslyFailedIndex) {
                if (cached != null) {
                    log.v("${i+1}/${items.size} '${item.step.name}' FAILED (skip)")
                    item.set(cached) // keep 'cached' for next run
                    headState = cached
                    headFresh = false
                    continue
                }
            }

            // Item failed: don't proceed. Return early.
            log.v("${i+1}/${items.size} '${item.step.name}' FAILED")
            return State.Wait(item.failure!!.sleep)
        }
        return when {
            items.isEmpty() -> State.Eos(Unit)
            headState is State.Eos -> State.Eos(Unit)
            else -> State.Ok(Unit)
        }
    }

    fun release() {
        items.forEach { it.step.release() }
    }

    companion object {
        internal fun build(name: String, builder: () -> Builder<*, Channel> = { Builder<Unit, Channel>() }): Pipeline {
            val items = builder().steps.map {
                @Suppress("UNCHECKED_CAST")
                PipelineItem(it as Step<Any, Channel, Any, Channel>)
            }
            return Pipeline(name, items)
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
