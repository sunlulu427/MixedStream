package com.astrastream.avpush.runtime

import com.astrastream.avpush.runtime.LogHelper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

open class ThreadImpl : IThread {

    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)
    private val pauseLock = Object()
    private var worker: Thread? = null
    private val tag = javaClass.simpleName

    override fun start(main: () -> Unit) {
        if (!running.compareAndSet(false, true)) {
            return
        }
        paused.set(false)
        worker = thread(start = true, isDaemon = true, name = "Astra-$tag") {
            try {
                main()
            } catch (error: Throwable) {
                LogHelper.e(tag, error, "worker crashed")
            } finally {
                running.set(false)
                paused.set(false)
                synchronized(pauseLock) { pauseLock.notifyAll() }
                LogHelper.d(tag) { "thread finished" }
            }
        }
    }

    override fun stop() {
        if (!running.get()) {
            return
        }
        running.set(false)
        paused.set(false)
        synchronized(pauseLock) { pauseLock.notifyAll() }
        val joiningThread = worker
        if (joiningThread != null && Thread.currentThread() != joiningThread) {
            try {
                joiningThread.join()
            } catch (_: InterruptedException) {
            }
        }
        worker = null
        LogHelper.d(tag) { "thread stop" }
    }

    override fun setPause(pause: Boolean) {
        if (!running.get()) {
            return
        }
        paused.set(pause)
        if (!pause) {
            synchronized(pauseLock) { pauseLock.notifyAll() }
        }
        LogHelper.d(tag) { "thread pause=$pause" }
    }

    override fun isPause(): Boolean = paused.get()

    override fun isRunning(): Boolean = running.get()

    protected fun awaitIfPaused() {
        if (!paused.get()) return
        synchronized(pauseLock) {
            while (paused.get() && running.get()) {
                pauseLock.wait()
            }
        }
    }
}
