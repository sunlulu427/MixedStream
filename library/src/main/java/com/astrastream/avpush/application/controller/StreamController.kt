package com.astrastream.avpush.application.controller

import android.content.Context
import android.media.MediaFormat
import com.astrastream.avpush.application.pipeline.AudioCaptureNode
import com.astrastream.avpush.application.pipeline.TransportNode
import com.astrastream.avpush.application.pipeline.VideoCaptureNode
import com.astrastream.avpush.core.pipeline.StreamingPipeline
import com.astrastream.avpush.core.utils.LogHelper
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.infrastructure.camera.Watermark
import com.astrastream.avpush.infrastructure.stream.sender.Sender
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
    private var audioSpecificConfig: ByteArray? = null

    private var pipeline: StreamingPipeline? = null
    private var audioNode: AudioCaptureNode? = null
    private var videoNode: VideoCaptureNode? = null
    private var transportNode: TransportNode? = null

    override fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        this.audioConfiguration = audioConfiguration
        applyAudioConfiguration()
    }

    override fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.videoConfiguration = videoConfiguration
        applyVideoConfiguration()
    }

    override fun setSender(sender: Sender) {
        this.sender = sender
        applyVideoConfiguration()
        applyAudioConfiguration()
    }

    override fun setStatsListener(listener: LiveStreamSession.StatsListener?) {
        statsListener = listener
        bindStatsListener()
    }

    override fun prepare(context: Context, textureId: Int, eglContext: EGLContext?) {
        this.appContext = context.applicationContext
        this.textureId = textureId
        this.eglContext = eglContext
        buildPipeline()
    }

    override fun start() {
        if (sender == null) {
            LogHelper.w(tag, "start ignored: sender not ready")
            return
        }
        if (pipeline == null || pipeline?.isEmpty() == true) {
            buildPipeline()
        }
        pipeline?.start()
        statsListener?.onVideoStats(0, videoConfiguration.fps)
    }

    override fun pause() {
        pipeline?.pause()
    }

    override fun resume() {
        pipeline?.resume()
    }

    override fun stop() {
        disposePipeline(true)
        statsListener?.onVideoStats(0, 0)
    }

    override fun setMute(isMute: Boolean) {
        audioNode?.setMute(isMute)
    }

    override fun setVideoBps(bps: Int) {
        videoNode?.setVideoBitrate(bps)
    }

    override fun setWatermark(watermark: Watermark) {
        this.watermark = watermark
        videoNode?.setWatermark(watermark)
    }

    private fun buildPipeline() {
        val context = appContext ?: return
        disposePipeline(true)
        val audioController = AudioController(audioConfiguration)
        val videoController = VideoController(context, textureId, eglContext, videoConfiguration)
        val transportNode = TransportNode { sender }
        val audioNode = AudioCaptureNode(audioController, ::handleAudioOutputFormat, ::handleError)
        val videoNode = VideoCaptureNode(videoController, ::handleVideoOutputFormat, ::handleError)
        audioNode.connect(transportNode.audioPad)
        videoNode.connect(transportNode.videoPad)
        this.audioNode = audioNode
        this.videoNode = videoNode
        this.transportNode = transportNode
        watermark?.let(videoNode::setWatermark)
        bindStatsListener()
        applyVideoConfiguration()
        applyAudioConfiguration()
        pipeline = StreamingPipeline().add(audioNode).add(videoNode).add(transportNode)
    }

    private fun handleAudioOutputFormat(outputFormat: MediaFormat?) {
        val ascBuffer = outputFormat?.getByteBuffer("csd-0")?.duplicate() ?: return
        ascBuffer.position(0)
        val asc = ByteArray(ascBuffer.remaining())
        ascBuffer.get(asc)
        audioSpecificConfig = asc
        applyAudioConfiguration()
    }

    private fun handleVideoOutputFormat(outputFormat: MediaFormat?) {
        if (outputFormat == null) return
    }

    private fun handleError(message: String?) {
        LogHelper.e(tag, message)
    }

    private fun applyAudioConfiguration() {
        sender?.configureAudio(audioConfiguration, audioSpecificConfig)
        transportNode?.updateAudioConfiguration(audioConfiguration, audioSpecificConfig)
    }

    private fun applyVideoConfiguration() {
        sender?.configureVideo(videoConfiguration)
        transportNode?.updateVideoConfiguration(videoConfiguration)
    }

    private fun bindStatsListener() {
        transportNode?.setStatsListener { bitrate, fps -> statsListener?.onVideoStats(bitrate, fps) }
    }

    private fun disposePipeline(shutdown: Boolean) {
        pipeline?.let {
            if (shutdown) {
                it.shutdown()
            } else {
                it.release()
            }
        }
        pipeline = null
        audioNode = null
        videoNode = null
        transportNode = null
    }
}
