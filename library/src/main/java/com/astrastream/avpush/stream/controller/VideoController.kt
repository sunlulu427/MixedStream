package com.astrastream.avpush.stream.controller

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import com.astrastream.avpush.domain.callback.IController
import com.astrastream.avpush.domain.callback.OnVideoEncodeListener
import com.astrastream.avpush.infrastructure.camera.CameraRecorder
import com.astrastream.avpush.infrastructure.camera.Watermark
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.runtime.LogHelper
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLContext


class VideoController(
    context: Context,
    textureId: Int,
    eglContext: EGLContext?,
    videoConfiguration: VideoConfiguration
) : IController, OnVideoEncodeListener {

    private var recorder = CameraRecorder(context, textureId, eglContext).also {
        it.prepare(videoConfiguration)
        it.setOnVideoEncodeListener(this)
    }

    private var mListener: IController.OnVideoDataListener? = null

    override fun start() {
        LogHelper.d(javaClass.simpleName) { "video recorder start" }
        recorder.start()
    }

    override fun stop() {
        LogHelper.d(javaClass.simpleName) { "video recorder stop" }
        recorder.stop()
    }

    override fun pause() {
        LogHelper.d(javaClass.simpleName) { "video recorder pause" }
        recorder.pause()
    }

    override fun resume() {
        LogHelper.d(javaClass.simpleName) { "video recorder resume" }
        recorder.resume()
    }

    override fun onVideoEncode(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        mListener?.onVideoData(bb, bi)
    }

    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        LogHelper.i(javaClass.simpleName) { "video encoder output format: ${outputFormat?.toString() ?: "null"}" }
        mListener?.onVideoOutformat(outputFormat)
    }

    override fun setVideoBps(bps: Int) {
        LogHelper.d(javaClass.simpleName) { "video bitrate request: $bps" }
        recorder.setEncodeBps(bps)
    }

    override fun setVideoDataListener(videoDataListener: IController.OnVideoDataListener) {
        mListener = videoDataListener
    }

    fun setWatermark(watermark: Watermark) {
        LogHelper.d(javaClass.simpleName) { "watermark update" }
        recorder.setWatermark(watermark)
    }
}
