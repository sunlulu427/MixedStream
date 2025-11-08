package com.astra.avpush.stream.controller

import android.content.Context
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.CameraRecorder
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog
import javax.microedition.khronos.egl.EGLContext


class VideoController(
    context: Context,
    textureId: Int,
    eglContext: EGLContext?,
    private var videoConfiguration: VideoConfiguration,
    private val senderProvider: () -> Sender?
) {

    private val recorder = CameraRecorder(context, textureId, eglContext).also {
        it.prepare(videoConfiguration)
    }

    fun start() {
        val sender = senderProvider() ?: run {
            AstraLog.w(javaClass.simpleName, "start skipped: sender not available")
            return
        }
        val surface = sender.prepareVideoSurface(videoConfiguration) ?: run {
            AstraLog.e(javaClass.simpleName, "Failed to obtain video surface from sender")
            return
        }
        AstraLog.d(javaClass.simpleName) { "video recorder start" }
        recorder.prepare(videoConfiguration)
        recorder.start(surface)
        sender.startVideo()
    }

    fun stop() {
        AstraLog.d(javaClass.simpleName) { "video recorder stop" }
        recorder.stop()
        senderProvider()?.run {
            stopVideo()
            releaseVideoSurface()
        }
    }

    fun pause() {
        AstraLog.d(javaClass.simpleName) { "video recorder pause" }
        recorder.pause()
    }

    fun resume() {
        AstraLog.d(javaClass.simpleName) { "video recorder resume" }
        recorder.resume()
    }

    fun setVideoBps(bps: Int) {
        AstraLog.d(javaClass.simpleName) { "video bitrate request: $bps" }
        senderProvider()?.updateVideoBps(bps)
    }

    fun setWatermark(watermark: Watermark) {
        AstraLog.d(javaClass.simpleName) { "watermark update" }
        recorder.setWatermark(watermark)
    }

    fun updateConfiguration(configuration: VideoConfiguration) {
        videoConfiguration = configuration
        recorder.prepare(configuration)
    }

}
