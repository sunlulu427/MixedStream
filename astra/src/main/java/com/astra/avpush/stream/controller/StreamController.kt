package com.astra.avpush.stream.controller

import android.content.Context
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog
import javax.microedition.khronos.egl.EGLContext

class StreamController : LiveStreamSession {

    private val tag = javaClass.simpleName

    private var watermark: Watermark? = null
    private var audioConfiguration = AudioConfiguration()
    private var videoConfiguration = VideoConfiguration()
    private var sender: Sender? = null
    private var statsListener: LiveStreamSession.StatsListener? = null

    private var appContext: Context? = null
    private var textureId: Int = 0
    private var eglContext: EGLContext? = null

    private var audioController: AudioController? = null
    private var videoController: VideoController? = null

    private val senderProvider = { sender }

    override fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        this.audioConfiguration = audioConfiguration
        AstraLog.d(tag) {
            "audio configuration updated: sampleRate=${audioConfiguration.sampleRate}, channels=${audioConfiguration.channelCount}"
        }
        sender?.configureAudio(audioConfiguration)
        if (audioController == null && appContext != null) {
            audioController = AudioController(audioConfiguration, senderProvider)
        } else {
            audioController?.updateConfiguration(audioConfiguration)
        }
    }

    override fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.videoConfiguration = videoConfiguration
        AstraLog.d(tag) {
            "video configuration updated: ${videoConfiguration.width}x${videoConfiguration.height}@${videoConfiguration.fps}"
        }
        sender?.configureVideo(videoConfiguration)
        videoController?.updateConfiguration(videoConfiguration)
    }

    override fun setSender(sender: Sender) {
        this.sender = sender
        AstraLog.d(tag) { "sender attached: ${sender.javaClass.simpleName}" }
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
        this.textureId = textureId
        this.eglContext = eglContext
        audioController = AudioController(audioConfiguration, senderProvider)
        videoController = VideoController(
            textureId,
            eglContext,
            videoConfiguration,
            senderProvider
        )
        watermark?.let { videoController?.setWatermark(it) }
        AstraLog.d(tag) { "stream controller prepared with texture=$textureId" }
    }

    override fun start() {
        val sender = sender ?: run {
            AstraLog.w(tag, "start ignored: sender not ready")
            return
        }
        ensureControllers()
        AstraLog.d(tag) { "starting streaming session" }
        sender.configureVideo(videoConfiguration)
        sender.configureAudio(audioConfiguration)
        videoController?.start()
        audioController?.start()
        statsListener?.onVideoStats(0, videoConfiguration.fps)
    }

    override fun pause() {
        AstraLog.d(tag) { "pausing streaming session" }
        audioController?.pause()
        videoController?.pause()
    }

    override fun resume() {
        AstraLog.d(tag) { "resuming streaming session" }
        audioController?.resume()
        videoController?.resume()
    }

    override fun stop() {
        AstraLog.d(tag) { "stopping streaming session" }
        audioController?.stop()
        videoController?.stop()
        statsListener?.onVideoStats(0, 0)
    }

    override fun setMute(isMute: Boolean) {
        AstraLog.d(tag) { "mute toggled: $isMute" }
        audioController?.setMute(isMute)
    }

    override fun setVideoBps(bps: Int) {
        AstraLog.d(tag) { "setting video bitrate=$bps" }
        sender?.updateVideoBps(bps)
    }

    override fun setWatermark(watermark: Watermark) {
        this.watermark = watermark
        AstraLog.d(tag) { "watermark updated" }
        videoController?.setWatermark(watermark)
    }

    private fun ensureControllers() {
        if (audioController == null) {
            audioController = AudioController(audioConfiguration, senderProvider)
        }
        if (videoController == null) {
            videoController = VideoController(
                textureId,
                eglContext,
                videoConfiguration,
                senderProvider
            )
            watermark?.let { videoController?.setWatermark(it) }
        }
    }
}
