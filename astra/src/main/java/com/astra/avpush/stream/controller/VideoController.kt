package com.astra.avpush.stream.controller

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import com.astra.avpush.domain.callback.IController
import com.astra.avpush.domain.callback.OnVideoEncodeListener
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.CameraRecorder
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.runtime.AstraLog
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLContext


class VideoController(
    context: Context,
    textureId: Int,
    eglContext: EGLContext?,
    videoConfiguration: VideoConfiguration
) : VideoSourceController, OnVideoEncodeListener {

    private var recorder = CameraRecorder(context, textureId, eglContext).also {
        it.prepare(videoConfiguration)
        it.setOnVideoEncodeListener(this)
    }

    private var mListener: IController.OnVideoDataListener? = null

    override fun start() {
        AstraLog.d(javaClass.simpleName) { "video recorder start" }
        recorder.start()
    }

    override fun stop() {
        AstraLog.d(javaClass.simpleName) { "video recorder stop" }
        recorder.stop()
    }

    override fun pause() {
        AstraLog.d(javaClass.simpleName) { "video recorder pause" }
        recorder.pause()
    }

    override fun resume() {
        AstraLog.d(javaClass.simpleName) { "video recorder resume" }
        recorder.resume()
    }

    override fun onVideoEncode(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        mListener?.onVideoData(bb, bi)
    }

    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        AstraLog.i(javaClass.simpleName) { "video encoder output format: ${outputFormat?.toString() ?: "null"}" }
        mListener?.onVideoOutformat(outputFormat)
    }

    override fun setVideoBps(bps: Int) {
        AstraLog.d(javaClass.simpleName) { "video bitrate request: $bps" }
        recorder.setEncodeBps(bps)
    }

    override fun setVideoDataListener(videoDataListener: IController.OnVideoDataListener) {
        mListener = videoDataListener
    }

    override fun setWatermark(watermark: Watermark) {
        AstraLog.d(javaClass.simpleName) { "watermark update" }
        recorder.setWatermark(watermark)
    }
}
