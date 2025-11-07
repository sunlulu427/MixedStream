package com.astra.avpush.infrastructure.camera

import android.content.Context
import android.view.Surface
import com.astra.avpush.domain.callback.IGLThreadConfig
import com.astra.avpush.domain.callback.IRenderer
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.renderer.EncodeRenderer
import com.astra.avpush.presentation.widget.GLSurfaceView
import java.lang.ref.WeakReference
import javax.microedition.khronos.egl.EGLContext

class CameraRecorder(
    context: Context,
    textureId: Int,
    private val eglContext: EGLContext?
) : IGLThreadConfig {

    private val renderer: EncodeRenderer = EncodeRenderer(context, textureId)
    private var rendererMode = GLSurfaceView.RENDERERMODE_CONTINUOUSLY
    private var glThread: EncodeRendererThread? = null
    private var surface: Surface? = null
    private var configuration: VideoConfiguration = VideoConfiguration()

    fun prepare(videoConfiguration: VideoConfiguration) {
        configuration = videoConfiguration
    }

    fun start(targetSurface: Surface) {
        surface = targetSurface
        glThread = EncodeRendererThread(WeakReference(this)).apply {
            setRendererSize(configuration.width, configuration.height)
            setRenderFps(configuration.fps)
            isCreate = true
            isChange = true
            start()
        }
    }

    fun stop() {
        glThread?.onDestory()
        glThread = null
        renderer.release()
        surface = null
    }

    fun pause() {
        glThread?.setPause()
    }

    fun resume() {
        glThread?.setResume()
    }

    override fun getSurface(): Surface? = surface
    override fun getEGLContext(): EGLContext? = eglContext
    override fun getRenderer(): IRenderer = renderer
    override fun getRendererMode(): Int = rendererMode

    fun setWatermark(watermark: Watermark) {
        renderer.setWatemark(watermark)
    }

    class EncodeRendererThread(
        weakReference: WeakReference<IGLThreadConfig>
    ) : GLThread(weakReference)
}
