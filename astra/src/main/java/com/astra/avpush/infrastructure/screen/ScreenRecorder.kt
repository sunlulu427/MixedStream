package com.astra.avpush.infrastructure.screen

import android.content.Context
import android.media.projection.MediaProjection
import android.view.Surface
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.runtime.AstraLog

class ScreenRecorder(
    private val context: Context
) {

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

    fun start(targetSurface: Surface) {
        val configuration = captureConfiguration
        if (configuration == null) {
            AstraLog.e(javaClass.simpleName, "screen recorder start requested without configuration")
            return
        }
        val screenRenderer = VulkanScreenRenderer(
            context = context,
            configuration = configuration,
            targetSurface = targetSurface
        )
        screenRenderer.updateProjection(mediaProjection)
        screenRenderer.start()
        renderer = screenRenderer
    }

    fun stop() {
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
