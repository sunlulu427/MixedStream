package com.astra.avpush.domain.callback

import android.view.Surface
import javax.microedition.khronos.egl.EGLContext

interface IGLThreadConfig {
    /**
     * 拿到渲染器
     */
    fun getRenderer(): IRenderer?

    /**
     * 拿到渲染的 Surface
     */
    fun getSurface(): Surface?

    /**
     * 拿到 EGL 环境的上下文
     */
    fun getEGLContext(): EGLContext?

    /**
     * 拿到渲染模式
     */
    fun getRendererMode(): Int
}
