package com.astrastream.avpush.stream.controller

import android.content.Context
import android.media.MediaFormat
import android.media.projection.MediaProjection
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.ScreenCaptureConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.infrastructure.camera.Watermark
import com.astrastream.avpush.infrastructure.stream.sender.Sender
import com.astrastream.avpush.runtime.LogHelper
import com.astrastream.avpush.stream.pipeline.AudioCaptureNode
import com.astrastream.avpush.stream.pipeline.StreamingPipeline
import com.astrastream.avpush.stream.pipeline.TransportNode
import com.astrastream.avpush.stream.pipeline.VideoCaptureNode

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

    private var pipeline: StreamingPipeline? = null
    private var audioNode: AudioCaptureNode? = null
    private var videoNode: VideoCaptureNode? = null
    private var transportNode: TransportNode? = null
    private var screenAudioController: ScreenAudioController? = null
    private var screenVideoController: ScreenVideoController? = null

    private var audioSpecificConfig: ByteArray? = null

    override fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        this.audioConfiguration = audioConfiguration
        LogHelper.d(tag) { "audio configuration updated for screen capture" }
        applyAudioConfiguration()
    }

    override fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.videoConfiguration = videoConfiguration
        screenConfiguration = screenConfiguration.copy(
            width = videoConfiguration.width,
            height = videoConfiguration.height,
            fps = videoConfiguration.fps
        )
        LogHelper.d(tag) { "video configuration updated for screen capture" }
        applyVideoConfiguration()
    }

    override fun setSender(sender: Sender) {
        this.sender = sender
        applyVideoConfiguration()
        applyAudioConfiguration()
    }

    override fun setStatsListener(listener: LiveStreamSession.StatsListener?) {
        statsListener = listener
        transportNode?.setStatsListener { bitrate, fps -> statsListener?.onVideoStats(bitrate, fps) }
    }

    override fun prepare(context: Context, textureId: Int, eglContext: javax.microedition.khronos.egl.EGLContext?) {
        appContext = context.applicationContext
        buildPipeline()
    }

    override fun start() {
        if (sender == null) {
            LogHelper.w(tag, "start ignored: sender missing")
            return
        }
        if (projection == null) {
            LogHelper.w(tag, "start ignored: projection missing")
            return
        }
        if (pipeline == null || pipeline?.isEmpty() == true) {
            buildPipeline()
        }
        LogHelper.d(tag) { "starting screen streaming" }
        pipeline?.start()
        statsListener?.onVideoStats(0, screenConfiguration.fps)
    }

    override fun pause() {
        pipeline?.pause()
    }

    override fun resume() {
        pipeline?.resume()
    }

    override fun stop() {
        pipeline?.stop()
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
        (videoNode ?: return).setWatermark(watermark)
    }

    override fun setScreenCapture(projection: MediaProjection?, configuration: ScreenCaptureConfiguration?) {
        this.projection = projection
        if (configuration != null) {
            this.screenConfiguration = configuration
        }
        screenAudioController?.updateProjection(projection)
        screenVideoController?.updateProjection(projection)
    }

    private fun buildPipeline() {
        val context = appContext ?: return
        val projection = projection ?: run {
            LogHelper.w(tag, "projection not yet available; pipeline deferred")
            return
        }
        disposePipeline(true)
        val audioController = ScreenAudioController(audioConfiguration, screenConfiguration, projection)
        val videoController = ScreenVideoController(context, screenConfiguration, videoConfiguration, projection)
        val transport = TransportNode { sender }
        val audioNode = AudioCaptureNode(audioController, ::handleAudioOutputFormat, ::handleError)
        val videoNode = VideoCaptureNode(videoController, ::handleVideoOutputFormat, ::handleError)
        audioNode.connect(transport.audioPad)
        videoNode.connect(transport.videoPad)
        watermark?.let(videoNode::setWatermark)
        transport.setStatsListener { bitrate, fps -> statsListener?.onVideoStats(bitrate, fps) }
        this.audioNode = audioNode
        this.videoNode = videoNode
        this.transportNode = transport
        screenAudioController = audioController
        screenVideoController = videoController
        pipeline = StreamingPipeline().add(audioNode).add(videoNode).add(transport)
        applyVideoConfiguration()
        applyAudioConfiguration()
        LogHelper.d(tag) { "screen pipeline initialised" }
    }

    private fun handleAudioOutputFormat(outputFormat: MediaFormat?) {
        val buffer = outputFormat?.getByteBuffer("csd-0")?.duplicate() ?: return
        buffer.position(0)
        val asc = ByteArray(buffer.remaining())
        buffer.get(asc)
        audioSpecificConfig = asc
        applyAudioConfiguration()
    }

    private fun handleVideoOutputFormat(outputFormat: MediaFormat?) {
        LogHelper.d(tag) { "video output format for screen: ${outputFormat?.toString() ?: "null"}" }
    }

    private fun handleError(message: String?) {
        LogHelper.e(tag, message)
    }

    private fun applyAudioConfiguration() {
        val sender = sender ?: return
        sender.configureAudio(audioConfiguration, audioSpecificConfig)
        transportNode?.updateAudioConfiguration(audioConfiguration, audioSpecificConfig)
    }

    private fun applyVideoConfiguration() {
        val sender = sender ?: return
        sender.configureVideo(videoConfiguration)
        transportNode?.updateVideoConfiguration(videoConfiguration)
    }

    private fun disposePipeline(shutdown: Boolean) {
        pipeline?.let { pipeline ->
            if (shutdown) pipeline.shutdown() else pipeline.release()
        }
        pipeline = null
        audioNode = null
        videoNode = null
        transportNode = null
        screenAudioController = null
        screenVideoController = null
    }
}
