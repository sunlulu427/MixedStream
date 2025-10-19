package com.astrastream.avpush.stream.pipeline

import com.astrastream.avpush.stream.controller.VideoController
import com.astrastream.avpush.stream.pipeline.core.PipelineRole
import com.astrastream.avpush.stream.pipeline.core.PipelineSource
import com.astrastream.avpush.stream.pipeline.frame.EncodedVideoFrame
import com.astrastream.avpush.runtime.LogHelper
import com.astrastream.avpush.domain.callback.IController
import com.astrastream.avpush.infrastructure.camera.Watermark
import android.media.MediaCodec
import android.media.MediaFormat
import java.nio.ByteBuffer


class VideoCaptureNode(
    private val controller: VideoController,
    private val onOutputFormat: (MediaFormat?) -> Unit,
    private val onError: (String?) -> Unit
) : PipelineSource<EncodedVideoFrame>("video-capture", PipelineRole.SOURCE),
    IController.OnVideoDataListener {

    private var stopped = false

    init {
        controller.setVideoDataListener(this)
    }

    override fun start() {
        LogHelper.d(name) { "starting video capture" }
        stopped = false
        controller.start()
    }

    override fun pause() {
        LogHelper.d(name) { "pausing video capture" }
        controller.pause()
    }

    override fun resume() {
        LogHelper.d(name) { "resuming video capture" }
        controller.resume()
    }

    override fun stop() {
        LogHelper.d(name) { "stopping video capture" }
        controller.stop()
        stopped = true
    }

    override fun release() {
        if (!stopped) {
            controller.stop()
        }
        LogHelper.d(name) { "releasing video capture node" }
        super.release()
    }

    override fun onVideoData(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        if (bb == null || bi == null) return
        emit(EncodedVideoFrame(bb, bi))
    }

    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        LogHelper.i(name) { "video codec output format changed: ${outputFormat?.toString() ?: "null"}" }
        onOutputFormat(outputFormat)
    }

    override fun onError(error: String?) {
        LogHelper.e(name, error)
        onError(error)
    }

    fun setVideoBitrate(bps: Int) {
        LogHelper.d(name) { "set video bitrate=$bps" }
        controller.setVideoBps(bps)
    }

    fun setWatermark(watermark: Watermark) {
        LogHelper.d(name) { "applying watermark: ${watermark.scale}" }
        controller.setWatermark(watermark)
    }
}
