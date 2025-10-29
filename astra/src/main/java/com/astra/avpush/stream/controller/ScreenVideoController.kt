package com.astra.avpush.stream.controller

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.projection.MediaProjection
import com.astra.avpush.domain.callback.IController
import com.astra.avpush.domain.callback.OnVideoEncodeListener
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.screen.ScreenRecorder
import com.astra.avpush.runtime.AstraLog
import java.nio.ByteBuffer

class ScreenVideoController(
    private val context: Context,
    private var screenConfiguration: ScreenCaptureConfiguration,
    videoConfiguration: VideoConfiguration,
    private var projection: MediaProjection?
) : VideoSourceController, OnVideoEncodeListener {

    private val recorder = ScreenRecorder(context).also {
        it.prepare(videoConfiguration)
        it.setOnVideoEncodeListener(this)
        it.updateConfiguration(screenConfiguration)
        it.updateProjection(projection)
    }

    private var listener: IController.OnVideoDataListener? = null

    fun updateProjection(projection: MediaProjection?) {
        this.projection = projection
        recorder.updateProjection(projection)
    }

    fun updateConfigurations(
        videoConfiguration: VideoConfiguration,
        screenCaptureConfiguration: ScreenCaptureConfiguration
    ) {
        recorder.prepare(videoConfiguration)
        this.screenConfiguration = screenCaptureConfiguration
        recorder.updateConfiguration(screenCaptureConfiguration)
    }

    override fun start() {
        AstraLog.d(javaClass.simpleName) { "screen recorder start" }
        recorder.start()
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
    }

    override fun onVideoEncode(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        listener?.onVideoData(bb, bi)
    }

    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        listener?.onVideoOutformat(outputFormat)
    }

    override fun setVideoDataListener(videoDataListener: IController.OnVideoDataListener) {
        listener = videoDataListener
    }

    override fun setVideoBps(bps: Int) {
        recorder.setEncodeBps(bps)
    }

    override fun setWatermark(watermark: Watermark) {
        AstraLog.d(javaClass.simpleName) { "watermark update ignored for screen capture" }
    }
}
