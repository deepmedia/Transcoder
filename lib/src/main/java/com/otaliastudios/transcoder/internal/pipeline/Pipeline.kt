package com.otaliastudios.transcoder.internal.pipeline

import com.otaliastudios.transcoder.internal.utils.Logger



private class PipelineItem(
    val step: Step<Any, Channel, Any, Channel>,
    val name: String,
) {
    // var success: State.Ok<Any>? = null
    // var failure: State.Retry? = null
    val unhandled = ArrayDeque<State.Ok<Any>>()
    var done = false
    var advanced = false
    var packets = 0
    private var nextUnhandled: ArrayDeque<State.Ok<Any>>? = null

    fun attachToNext(next: PipelineItem) {
        nextUnhandled = next.unhandled
        step.initialize(next = next.step.channel)
    }

    fun canHandle(first: Boolean): Boolean {
        if (done) return false
        if (first) {
            unhandled.clear()
            unhandled.addLast(State.Ok(Unit))
        }
        return unhandled.isNotEmpty() || step is QueuedStep
    }

    fun handle(): State.Failure? {
        advanced = false
        while (unhandled.isNotEmpty() && !done) {
            val input = unhandled.removeFirst()
            when (val result = step.advance(input)) {
                is State.Ok -> {
                    packets++
                    advanced = true
                    done = result is State.Eos
                    nextUnhandled?.addLast(result)
                }
                is State.Retry -> {
                    unhandled.addFirst(input)
                    return result
                }
                is State.Consume -> return result
            }
        }
        if (!advanced && !done && step is QueuedStep) {
            when (val result = step.tryAdvance()) {
                is State.Ok -> {
                    packets++
                    advanced = true
                    done = result is State.Eos
                    nextUnhandled?.addLast(result)
                }
                is State.Failure -> return result
            }
        }
        return null
    }
}

internal class Pipeline private constructor(name: String, private val items: List<PipelineItem>) {

    private val log = Logger(name)

    init {
        items.zipWithNext().reversed().forEach { (first, next) -> first.attachToNext(next) }
    }

    fun execute(): State<Unit> {
        log.v("LOOP")
        var advanced = false
        var sleeps = false

        for (i in items.indices) {
            val item = items[i]

            if (item.canHandle(i == 0)) {
                log.v("${item.name} START #${item.packets} (${item.unhandled.size} pending)")
                val failure = item.handle()
                if (failure != null) {
                    sleeps = sleeps || failure.sleep
                    log.v("${item.name} FAILED #${item.packets}")
                } else {
                    log.v("${item.name} SUCCESS #${item.packets} ${if (item.done) "(eos)" else ""}")
                }
                advanced = advanced || item.advanced
            } else {
                log.v("${item.name} SKIP #${item.packets} ${if (item.done) "(eos)" else ""}")
            }
        }
        return when {
            items.isEmpty() -> State.Eos(Unit)
            items.last().done -> State.Eos(Unit)
            advanced -> State.Ok(Unit)
            else -> State.Retry(sleeps)
        }
    }

    /*fun execute_OLD(): State<Unit> {
        var headState: State.Ok<Any> = State.Ok(Unit)
        var headFresh = true
        // In case of failure in the previous run, we should re-run all items before the failed one
        // This is important for decoders/encoders that need more input before they can output
        val previouslyFailedIndex = items.indexOfLast { it.failure != null }.takeIf { it >= 0 }
        log.v("LOOP (previouslyFailed: ${previouslyFailedIndex})")
        for (i in items.indices) {
            val item = items[i]
            val cached = item.success
            val skip = cached is State.Eos || (!headFresh && cached != null)
            if (skip) {
                // Note: we could consider a retry() on queued steps here but it's risky
                // because the current 'cached' value may have never been handled by the next item
                log.v("${i+1}/${items.size} '${item.step.name}' SKIP ${if (cached is State.Eos) "(eos)" else "(handled)"}")
                headState = cached!!
                headFresh = false
                continue
            }
            // This item did not succeed at the last loop, or we have fresh input data.
            log.v("${i+1}/${items.size} '${item.step.name}' START (${if (headFresh) "fresh" else "stale"})")
            val result = when {
                !headFresh && item.step is QueuedStep -> item.step.retry() // queued steps should never get stale data
                else -> item.step.advance(headState)
            }
            item.set(result)
            if (result is State.Ok) {
                log.v("${i+1}/${items.size} '${item.step.name}' SUCCESS ${if (result is State.Eos) "(eos)" else ""}")
                headState = result
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
    } */

    fun release() {
        items.forEach { it.step.release() }
    }

    companion object {
        internal fun build(name: String, debug: String? = null, builder: () -> Builder<*, Channel> = { Builder<Unit, Channel>() }): Pipeline {
            val steps = builder().steps
            val items = steps.mapIndexed { index, step ->
                @Suppress("UNCHECKED_CAST")
                PipelineItem(
                    step = step as Step<Any, Channel, Any, Channel>,
                    name = "${index+1}/${steps.size} '${step.name}'"
                )
            }
            return Pipeline("${name}Pipeline${debug ?: ""}", items)
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
