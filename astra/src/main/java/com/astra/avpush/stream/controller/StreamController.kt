package com.astra.avpush.stream.controller

import android.content.Context
import android.media.MediaFormat
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.stream.pipeline.AudioCaptureNode
import com.astra.avpush.stream.pipeline.StreamingPipeline
import com.astra.avpush.stream.pipeline.TransportNode
import com.astra.avpush.stream.pipeline.VideoCaptureNode
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
        AstraLog.d(tag) { "audio configuration updated: sampleRate=${audioConfiguration.sampleRate}, channels=${audioConfiguration.channelCount}" }
        applyAudioConfiguration()
    }

    override fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.videoConfiguration = videoConfiguration
        AstraLog.d(tag) { "video configuration updated: ${videoConfiguration.width}x${videoConfiguration.height}@${videoConfiguration.fps}" }
        applyVideoConfiguration()
    }

    override fun setSender(sender: Sender) {
        this.sender = sender
        AstraLog.d(tag) { "sender attached: ${sender.javaClass.simpleName}" }
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
        AstraLog.d(tag) { "preparing stream controller with texture=$textureId" }
        buildPipeline()
    }

    override fun start() {
        if (sender == null) {
            AstraLog.w(tag, "start ignored: sender not ready")
            return
        }
        if (pipeline == null || pipeline?.isEmpty() == true) {
            buildPipeline()
        }
        AstraLog.d(tag) { "starting streaming session" }
        pipeline?.start()
        statsListener?.onVideoStats(0, videoConfiguration.fps)
    }

    override fun pause() {
        AstraLog.d(tag) { "pausing streaming session" }
        pipeline?.pause()
    }

    override fun resume() {
        AstraLog.d(tag) { "resuming streaming session" }
        pipeline?.resume()
    }

    override fun stop() {
        AstraLog.d(tag) { "stopping streaming session" }
        disposePipeline(true)
        statsListener?.onVideoStats(0, 0)
    }

    override fun setMute(isMute: Boolean) {
        AstraLog.d(tag) { "mute toggled: $isMute" }
        audioNode?.setMute(isMute)
    }

    override fun setVideoBps(bps: Int) {
        AstraLog.d(tag) { "setting video bitrate=$bps" }
        videoNode?.setVideoBitrate(bps)
    }

    override fun setWatermark(watermark: Watermark) {
        this.watermark = watermark
        AstraLog.d(tag) { "watermark updated" }
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
        AstraLog.d(tag) { "pipeline initialised" }
    }

    private fun handleAudioOutputFormat(outputFormat: MediaFormat?) {
        val ascBuffer = outputFormat?.getByteBuffer("csd-0")?.duplicate() ?: return
        ascBuffer.position(0)
        val asc = ByteArray(ascBuffer.remaining())
        ascBuffer.get(asc)
        audioSpecificConfig = asc
        AstraLog.d(tag) { "audio specific config extracted (${asc.size} bytes)" }
        applyAudioConfiguration()
    }

    private fun handleVideoOutputFormat(outputFormat: MediaFormat?) {
        if (outputFormat == null) return
        AstraLog.d(tag) { "video output format reported: ${outputFormat.toString()}" }
    }

    private fun handleError(message: String?) {
        AstraLog.e(tag, message)
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
        AstraLog.d(tag) { "pipeline disposed" }
    }
}
