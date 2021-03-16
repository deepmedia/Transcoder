package com.otaliastudios.transcoder.internal.utils

internal fun stackTrace() = Thread.currentThread().stackTrace.drop(2).take(10).joinToString("\n")