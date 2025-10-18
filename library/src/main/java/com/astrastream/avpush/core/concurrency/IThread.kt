package com.astrastream.avpush.core.concurrency

interface IThread {
    fun start(main: () -> Unit)
    fun stop()
    fun setPause(pause: Boolean)
    fun isPause(): Boolean
    fun isRunning(): Boolean
}
