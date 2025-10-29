package com.astra.avpush.runtime

interface IThread {
    fun start(main: () -> Unit)
    fun stop()
    fun setPause(pause: Boolean)
    fun isPause(): Boolean
    fun isRunning(): Boolean
}
