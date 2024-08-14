package com.otaliastudios.transcoder.internal.pipeline

internal sealed interface State<out T> {

    // Running
    open class Ok<T>(val value: T) : State<T> {
        override fun toString() = "State.Ok($value)"
    }

    // Run for the last time
    class Eos<T>(value: T) : Ok<T>(value) {
        override fun toString() = "State.Eos($value)"
    }

    // Failed to produce output, try again later
    sealed interface Failure : State<Nothing> {
        val sleep: Boolean
    }

    class Retry(override val sleep: Boolean) : Failure {
        override fun toString() = "State.Retry($sleep)"
    }

    class Consume(override val sleep: Boolean = false) : Failure {
        override fun toString() = "State.Consume($sleep)"
    }
}
