package com.astra.avpush.stream.controller

import android.content.Context
import android.media.projection.MediaProjection
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog
import javax.microedition.khronos.egl.EGLContext

class ScreenStreamController : LiveStreamSession {

    private val tag = javaClass.simpleName

    private var audioConfiguration = AudioConfiguration(channelCount = 2)
    private var videoConfiguration = VideoConfiguration()
    private var screenConfiguration = ScreenCaptureConfiguration(
        width = videoConfiguration.width,
        height = videoConfiguration.height,
        densityDpi = 320,
        fps = videoConfiguration.fps
    )
    private var projection: MediaProjection? = null
    private var watermark: Watermark? = null
    private var sender: Sender? = null
    private var statsListener: LiveStreamSession.StatsListener? = null

    private var appContext: Context? = null

    private var audioController: ScreenAudioController? = null
    private var videoController: ScreenVideoController? = null

    private val senderProvider = { sender }

    override fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        this.audioConfiguration = audioConfiguration
        sender?.configureAudio(audioConfiguration)
    }

    override fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.videoConfiguration = videoConfiguration
        screenConfiguration = screenConfiguration.copy(
            width = videoConfiguration.width,
            height = videoConfiguration.height,
            fps = videoConfiguration.fps
        )
        sender?.configureVideo(videoConfiguration)
        videoController?.updateConfigurations(videoConfiguration, screenConfiguration)
    }

    override fun setSender(sender: Sender) {
        this.sender = sender
        sender.setOnStatsListener { bitrate, fps -> statsListener?.onVideoStats(bitrate, fps) }
        sender.configureVideo(videoConfiguration)
        sender.configureAudio(audioConfiguration)
    }

    override fun setStatsListener(listener: LiveStreamSession.StatsListener?) {
        statsListener = listener
        sender?.setOnStatsListener { bitrate, fps -> statsListener?.onVideoStats(bitrate, fps) }
    }

    override fun prepare(context: Context, textureId: Int, eglContext: EGLContext?) {
        appContext = context.applicationContext
        audioController = ScreenAudioController(audioConfiguration, screenConfiguration, projection, senderProvider)
        videoController = ScreenVideoController(
            context.applicationContext,
            screenConfiguration,
            videoConfiguration,
            projection,
            senderProvider
        )
    }

    override fun start() {
        val sender = sender ?: run {
            AstraLog.w(tag, "screen start ignored: sender missing")
            return
        }
        val projection = projection ?: run {
            AstraLog.w(tag, "screen start ignored: projection missing")
            return
        }
        ensureControllers()
        videoController?.updateProjection(projection)
        audioController?.updateProjection(projection)
        sender.configureVideo(videoConfiguration)
        sender.configureAudio(audioConfiguration)
        videoController?.start()
        audioController?.start()
        statsListener?.onVideoStats(0, screenConfiguration.fps)
    }

    override fun pause() {
        audioController?.pause()
        videoController?.pause()
    }

    override fun resume() {
        audioController?.resume()
        videoController?.resume()
    }

    override fun stop() {
        audioController?.stop()
        videoController?.stop()
        statsListener?.onVideoStats(0, 0)
    }

    override fun setMute(isMute: Boolean) {
        audioController?.setMute(isMute)
    }

    override fun setVideoBps(bps: Int) {
        sender?.updateVideoBps(bps)
    }

    override fun setWatermark(watermark: Watermark) {
        this.watermark = watermark
        AstraLog.d(tag) { "watermark ignored for screen stream" }
    }

    override fun setScreenCapture(projection: MediaProjection?, configuration: ScreenCaptureConfiguration?) {
        this.projection = projection
        if (configuration != null) {
            this.screenConfiguration = configuration
        }
        audioController?.updateProjection(projection)
        videoController?.updateProjection(projection)
    }

    private fun ensureControllers() {
        if (audioController == null) {
            audioController = ScreenAudioController(audioConfiguration, screenConfiguration, projection, senderProvider)
        }
        if (videoController == null && appContext != null) {
            videoController = ScreenVideoController(
                appContext!!,
                screenConfiguration,
                videoConfiguration,
                projection,
                senderProvider
            )
        }
    }
}
