package com.astra.avpush.stream.controller

import android.content.Context
import android.media.projection.MediaProjection
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.screen.ScreenRecorder
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog

class ScreenVideoController(
    private val context: Context,
    private var screenConfiguration: ScreenCaptureConfiguration,
    private var videoConfiguration: VideoConfiguration,
    private var projection: MediaProjection?,
    private val senderProvider: () -> Sender?
) : VideoSourceController {

    private val recorder = ScreenRecorder(context).also {
        it.updateConfiguration(screenConfiguration)
        it.updateProjection(projection)
    }

    fun updateProjection(projection: MediaProjection?) {
        this.projection = projection
        recorder.updateProjection(projection)
    }

    fun updateConfigurations(
        videoConfiguration: VideoConfiguration,
        screenCaptureConfiguration: ScreenCaptureConfiguration
    ) {
        this.screenConfiguration = screenCaptureConfiguration
        recorder.updateConfiguration(screenCaptureConfiguration)
        recorder.updateProjection(projection)
        this.videoConfiguration = videoConfiguration
    }

    override fun start() {
        val sender = senderProvider() ?: run {
            AstraLog.w(javaClass.simpleName, "screen recorder start skipped: sender missing")
            return
        }
        val surface = sender.prepareVideoSurface(videoConfiguration) ?: run {
            AstraLog.e(javaClass.simpleName, "Failed to acquire screen surface")
            return
        }
        AstraLog.d(javaClass.simpleName) { "screen recorder start" }
        recorder.start(surface)
        sender.startVideo()
    }

    override fun pause() {
        AstraLog.d(javaClass.simpleName) { "screen recorder pause" }
        recorder.pause()
    }

    override fun resume() {
        AstraLog.d(javaClass.simpleName) { "screen recorder resume" }
        recorder.resume()
    }

    override fun stop() {
        AstraLog.d(javaClass.simpleName) { "screen recorder stop" }
        recorder.stop()
        senderProvider()?.run {
            stopVideo()
            releaseVideoSurface()
        }
    }

    override fun setVideoBps(bps: Int) {
        senderProvider()?.updateVideoBps(bps)
    }

    override fun setWatermark(watermark: Watermark) {
        AstraLog.d(javaClass.simpleName) { "watermark update ignored for screen capture" }
    }
}
