package com.astra.avpush.infrastructure.stream.nativebridge

import java.util.concurrent.atomic.AtomicLong

internal object NativeSenderHandleGenerator {
    private val counter = AtomicLong(1L)

    fun next(): Long = counter.getAndIncrement()
}
