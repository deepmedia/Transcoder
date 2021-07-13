package com.otaliastudios.transcoder.internal.pipeline

sealed class State<out T> {

    // Running
    open class Ok<T>(val value: T) : State<T>() {
        override fun toString() = "State.Ok($value)"
    }

    // Run for the last time
    class Eos<T>(value: T) : Ok<T>(value) {
        override fun toString() = "State.Eos($value)"
    }

    // couldn't run, but might in the future
    object Wait : State<Nothing>() {
        override fun toString() = "State.Wait"
    }

    // call again as soon as possible
    object Retry : State<Nothing>() {
        override fun toString() = "State.Retry"
    }
}
