package com.astra.avpush.runtime

import java.util.concurrent.atomic.AtomicBoolean

internal object NativeLogger {

    init {
        System.loadLibrary("astra")
    }

    private val configured = AtomicBoolean(false)

    fun initialise(path: String) {
        if (path.isBlank()) return
        if (configured.compareAndSet(false, true)) {
            nativeInit(path)
        }
    }

    fun write(level: Int, tag: String, message: String) {
        if (!configured.get()) return
        nativeWrite(level, tag, message)
    }

    private external fun nativeInit(path: String)
    private external fun nativeWrite(level: Int, tag: String, message: String)
}
