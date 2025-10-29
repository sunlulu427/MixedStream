package com.astra.avpush.stream.pipeline

import com.astra.avpush.stream.controller.VideoSourceController
import com.astra.avpush.stream.pipeline.core.PipelineRole
import com.astra.avpush.stream.pipeline.core.PipelineSource
import com.astra.avpush.stream.pipeline.frame.EncodedVideoFrame
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.domain.callback.IController
import com.astra.avpush.infrastructure.camera.Watermark
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer


class VideoCaptureNode(
    private val controller: VideoSourceController,
    private val onOutputFormat: (MediaFormat?) -> Unit,
    private val onError: (String?) -> Unit
) : PipelineSource<EncodedVideoFrame>("video-capture", PipelineRole.SOURCE),
    IController.OnVideoDataListener {

    private var stopped = false

    init {
        controller.setVideoDataListener(this)
    }

    override fun start() {
        AstraLog.d(name) { "starting video capture" }
        stopped = false
        controller.start()
    }

    override fun pause() {
        AstraLog.d(name) { "pausing video capture" }
        controller.pause()
    }

    override fun resume() {
        AstraLog.d(name) { "resuming video capture" }
        controller.resume()
    }

    override fun stop() {
        AstraLog.d(name) { "stopping video capture" }
        controller.stop()
        stopped = true
    }

    override fun release() {
        if (!stopped) {
            controller.stop()
        }
        AstraLog.d(name) { "releasing video capture node" }
        super.release()
    }

    override fun onVideoData(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        if (bb == null || bi == null) return
        emit(EncodedVideoFrame(bb, bi))
    }

    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        AstraLog.i(name) { "video codec output format changed: ${outputFormat?.toString() ?: "null"}" }
        onOutputFormat(outputFormat)
    }

    override fun onError(error: String?) {
        AstraLog.e(name, error)
        onError(error)
    }

    fun setVideoBitrate(bps: Int) {
        AstraLog.d(name) { "set video bitrate=$bps" }
        controller.setVideoBps(bps)
    }

    fun setWatermark(watermark: Watermark) {
        AstraLog.d(name) { "applying watermark: ${watermark.scale}" }
        controller.setWatermark(watermark)
    }
}
