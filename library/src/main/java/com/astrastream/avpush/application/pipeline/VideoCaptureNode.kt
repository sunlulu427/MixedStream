package com.astrastream.avpush.application.pipeline

import android.media.MediaCodec
import android.media.MediaFormat
import com.astrastream.avpush.application.controller.VideoController
import com.astrastream.avpush.core.pipeline.PipelineRole
import com.astrastream.avpush.core.pipeline.PipelineSource
import com.astrastream.avpush.core.pipeline.frame.EncodedVideoFrame
import com.astrastream.avpush.domain.callback.IController
import com.astrastream.avpush.infrastructure.camera.Watermark
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
        stopped = false
        controller.start()
    }

    override fun pause() {
        controller.pause()
    }

    override fun resume() {
        controller.resume()
    }

    override fun stop() {
        controller.stop()
        stopped = true
    }

    override fun release() {
        if (!stopped) {
            controller.stop()
        }
        super.release()
    }

    override fun onVideoData(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        if (bb == null || bi == null) return
        emit(EncodedVideoFrame(bb, bi))
    }

    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        onOutputFormat(outputFormat)
    }

    override fun onError(error: String?) {
        onError(error)
    }

    fun setVideoBitrate(bps: Int) {
        controller.setVideoBps(bps)
    }

    fun setWatermark(watermark: Watermark) {
        controller.setWatermark(watermark)
    }
}
