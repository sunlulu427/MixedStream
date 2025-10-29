package com.astra.avpush.presentation.widget

import android.content.Context
import android.util.AttributeSet
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.astra.avpush.domain.callback.IGLThreadConfig
import com.astra.avpush.domain.callback.IRenderer
import com.astra.avpush.domain.config.RendererConfiguration
import com.astra.avpush.infrastructure.camera.GLThread
import com.astra.avpush.runtime.LogHelper
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGLContext

open class GLSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : SurfaceView(context, attrs, defStyleAttr), SurfaceHolder.Callback, IGLThreadConfig {
    init {
        holder.addCallback(this)
    }

    val TAG = javaClass.simpleName


    /**
     * 渲染模式-默认自动模式
     */
    private var mRendererMode = RENDERERMODE_CONTINUOUSLY

    /**
     * 渲染配置
     */
    private var mRendererConfiguration = RendererConfiguration()

    /**
     * GLES 渲染线程
     */
    private lateinit var mEglThread: GLSurfaceThread


    private var mSurface: Surface? = null

    private var mEGLContext: EGLContext? = null


    companion object {
        /**
         * 手动调用渲染
         */
        const val RENDERERMODE_WHEN_DIRTY = 0

        /**
         * 自动渲染
         */
        const val RENDERERMODE_CONTINUOUSLY = 1
    }

    /**
     * 渲染器
     */
    var mRenderer: IRenderer? = null

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (mSurface == null) {
            mSurface = holder.surface
        }
        this.mEglThread = GLSurfaceThread(WeakReference(this))
        this.mEglThread.isCreate = true
        this.mEglThread.start()
    }


    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mEglThread.let { eglThread ->
            eglThread.setRendererSize(width, height)
            eglThread.isChange = true
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mEglThread.let {
            mEglThread.onDestory()
        }
    }

    /**
     * 配置渲染属性
     */
    fun configure(rendererConfiguration: RendererConfiguration) {
        this.mRendererConfiguration = rendererConfiguration
        this.mRenderer = mRendererConfiguration.renderer;
        this.mRendererMode = mRendererConfiguration.rendererMode;
        mRendererConfiguration.eglContext?.let {
            this.mEGLContext = it
        }
        if (this::mEglThread.isInitialized) {
            LogHelper.d(TAG, "configure: renderer updated, force recreate (mode=$mRendererMode)")
            mEglThread.isCreate = true
            mEglThread.isChange = true
            if (mRendererMode == RENDERERMODE_WHEN_DIRTY) {
                requestRenderer()
            }
        }
    }

    /**
     * 拿到 EGL 上下文
     */
    override fun getEGLContext(): EGLContext? {
        if (mEGLContext == null)
            return mEglThread.getEGLContext()
        return mEGLContext
    }

    /**
     * 外部请求渲染刷新
     */
    fun requestRenderer() = mEglThread.requestRenderer()

    /**
     * 得到渲染器
     */
    override fun getRenderer(): IRenderer? = mRenderer

    /**
     * 得到渲染模式
     */
    override fun getRendererMode(): Int = mRendererMode

    /**
     * 得到渲染 Surface
     */
    override fun getSurface(): Surface? = mSurface

    /**
     * 自定义GLThread线程类，主要用于OpenGL的绘制操作
     */
    class GLSurfaceThread(weakReference: WeakReference<IGLThreadConfig>) :
        GLThread(weakReference) {
        /**
         * 获取 EGL 上下文环境
         */
        fun getEGLContext(): EGLContext? = mEGLHelper.eglContext
    }
}
