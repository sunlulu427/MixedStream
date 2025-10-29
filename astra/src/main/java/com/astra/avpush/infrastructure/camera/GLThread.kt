package com.astra.avpush.infrastructure.camera

import com.astra.avpush.domain.callback.IGLThreadConfig
import com.astra.avpush.presentation.widget.GLSurfaceView
import com.astra.avpush.runtime.AstraLog
import java.lang.ref.WeakReference
import kotlin.math.max

open class GLThread(private val weakReference: WeakReference<IGLThreadConfig>) : Thread() {

    private var TAG = this.javaClass.simpleName
    /**
     * EGL 环境搭建帮助类
     */
    protected lateinit var mEGLHelper: EglHelper
    /**
     * 对象锁
     */
    private val mLock = Object()

    /**
     * 是否退出线程
     */
    private var isExit = false;

    /**
     * 是否创建线程
     */
    internal var isCreate = false

    /**
     * 窗口是否改变
     */
    internal var isChange = false

    /**
     * 是否开始渲染
     */
    private var isStart = false

    /**
     * 是否停止
     */
    private var isPause = false

    /**
     * 刷新帧率
     */
    private var mDrawFpsRate = 60L
    private var frameIntervalMs = max(1L, 1000L / mDrawFpsRate)

    /**
     * 渲染的 size
     */
    private var mWidth = 1080
    private var mHeight = 1920;

    override fun run() {
        super.run()
        //实例化 EGL 环境搭建的帮组类
        mEGLHelper = EglHelper()
        //初始化 EGL
        weakReference.get()?.let { thread ->
            mEGLHelper.create(thread.getSurface(), thread.getEGLContext())

            while (true) {
                if (isExit) {
                    release()
                    break
                }

                if (isPause) {
                    try {
                        sleep(500)
                        continue
                    } catch (error: InterruptedException) {
                        AstraLog.e(TAG, error.message)
                    }
                }
                if (isStart) {
                    //判断是手动刷新还是自动 刷新
                    if (thread.getRendererMode() == GLSurfaceView.Companion.RENDERERMODE_WHEN_DIRTY) {
                        synchronized(mLock) {
                            try {
                                mLock.wait()
                            } catch (error: InterruptedException) {
                                AstraLog.e(TAG, error.message)
                            }
                        }

                    } else if (thread.getRendererMode() == GLSurfaceView.Companion.RENDERERMODE_CONTINUOUSLY) {
                        try {
                            sleep(frameIntervalMs)
                        } catch (error: InterruptedException) {
                            AstraLog.e(TAG, error.message)
                        }
                    } else {
                        throw RuntimeException("mRendererMode is wrong value");
                    }
                }
                //开始创建
                onCreate(mWidth, mHeight)
                //改变窗口
                onChange(mWidth, mHeight)
                //开始绘制
                onDraw()
                this.isStart = true
            }
        }
    }

    /**
     * 渲染窗口的大小
     */
    fun setRendererSize(width: Int, height: Int) {
        this.mWidth = width
        this.mHeight = height
    }

    fun setRenderFps(fps: Int) {
        val sanitized = fps.coerceIn(1, 120)
        mDrawFpsRate = sanitized.toLong()
        frameIntervalMs = max(1L, 1000L / sanitized)
    }

    /**
     * 渲染器可以创建了
     */
    private fun onCreate(width: Int, height: Int) {
        if (!isCreate) {
            return
        }
        weakReference.get()?.let { view ->
            this.isCreate = false
            view.getRenderer()?.onSurfaceCreate(width, height)
        }
    }

    /**
     * 渲染器需要改变窗口大小
     */
    private fun onChange(width: Int, height: Int) {
        if (!isChange) {
            return
        }
        weakReference.get()?.let { view ->
            this.isChange = false
            view.getRenderer()?.onSurfaceChange(width, height)
        }
    }


    /**
     * 停止渲染
     */
    fun setPause() {
        isPause = true
    }

    /**
     * 恢复渲染
     */
    fun setResume() {
        isPause = false
    }

    /**
     * 渲染器可以开始绘制了
     */
    private fun onDraw() {
        weakReference.get()?.let { view ->
            view.getRenderer()?.onDraw()
            if (!isStart)
                view.getRenderer()?.onDraw()

            this.mEGLHelper.swapBuffers()
        }
    }

    /**
     * 手动请求刷新
     */
    fun requestRenderer() {
        synchronized(mLock) {
            try {
                mLock.notifyAll()
            } catch (error: Exception) {
                AstraLog.e(TAG, error.message)
            }
        }
    }

    fun onDestory() {
        this.isExit = true
        //避免线程睡眠这里重新刷新一次
        requestRenderer()
    }


    /**
     * 释放资源
     */
    private fun release() {
        mEGLHelper.release()
        weakReference.clear()
    }
}
