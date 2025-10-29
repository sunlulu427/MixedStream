package com.astra.avpush.infrastructure.screen

import android.content.Context
import android.media.projection.MediaProjection
import android.view.Surface
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.infrastructure.codec.VideoEncoder
import com.astra.avpush.runtime.LogHelper

class ScreenRecorder(
    private val context: Context
) : VideoEncoder() {

    private var captureConfiguration: ScreenCaptureConfiguration? = null
    private var mediaProjection: MediaProjection? = null
    private var renderer: VulkanScreenRenderer? = null

    fun updateConfiguration(configuration: ScreenCaptureConfiguration) {
        captureConfiguration = configuration
        renderer?.updateConfiguration(configuration)
    }

    fun updateProjection(projection: MediaProjection?) {
        mediaProjection = projection
        renderer?.updateProjection(projection)
    }

    override fun onSurfaceCreate(surface: Surface?) {
        super.onSurfaceCreate(surface)
        val targetSurface = surface ?: return
        val configuration = captureConfiguration
        if (configuration == null) {
            LogHelper.e(javaClass.simpleName, "screen recorder surface ready but configuration missing")
            return
        }
        val renderer = VulkanScreenRenderer(
            context = context,
            configuration = configuration,
            targetSurface = targetSurface
        )
        renderer.updateProjection(mediaProjection)
        renderer.start()
        this.renderer = renderer
    }

    override fun stop() {
        super.stop()
        renderer?.stop()
        renderer = null
    }

    override fun onSurfaceDestory(surface: Surface?) {
        super.onSurfaceDestory(surface)
        renderer?.stop()
        renderer = null
    }

    fun pause() {
        renderer?.pause()
    }

    fun resume() {
        renderer?.resume()
    }
}
