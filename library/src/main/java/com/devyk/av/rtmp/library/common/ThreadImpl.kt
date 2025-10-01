package com.devyk.av.rtmp.library.common

import com.devyk.av.rtmp.library.utils.LogHelper

open class ThreadImpl : IThread {

    private var isPause = false

    private var isRuning = false

    private var TAG = javaClass.simpleName


    override fun start(main: () -> Unit) {
        if (isRunning())return
        isRuning = true
        isPause = false
        Thread {
            main()
            LogHelper.d(TAG, "thread start!")
        }.start()
    }

    /**
     * 线程停止
     */
    override fun stop() {
        isRuning = false
        isPause = true
        LogHelper.d(TAG, "thread stop!")
    }

    /**
     * 设置停止
     */
    override fun setPause(pause: Boolean) {
        this.isPause = pause
        LogHelper.d(TAG, "thread pause:${pause}!")
    }

    /**
     * 是否停止
     */
    override fun isPause(): Boolean = isPause

    /**
     * 是否执行
     */
    override fun isRunning(): Boolean = isRuning
}