package com.devyk.av.rtmp.library.common

interface IThread {

    /**
     * 开始执行线程
     */
    fun start(main:()->Unit)

    /**
     * 停止执行
     */
    fun stop()

    /**
     *设置是否暂停
     */
    fun setPause(pause: Boolean)

    /**
     * 停止
     */
    fun isPause(): Boolean

    /**
     * 是否运行
     */
    fun isRunning(): Boolean
}