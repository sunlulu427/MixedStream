package com.astrastream.avpush.application.controller

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import com.astrastream.avpush.domain.callback.IController
import com.astrastream.avpush.infrastructure.camera.Watermark
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.infrastructure.stream.sender.Sender
import com.astrastream.avpush.core.utils.LogHelper
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLContext
import kotlin.math.roundToInt

class StreamController : LiveStreamSession, IController.OnAudioDataListener,
    IController.OnVideoDataListener {
    private val TAG = javaClass.simpleName

    /**
     * 水印
     */
    private var mWatermark: Watermark? = null

    /**
     * 音频数据的管理
     */
    private var mAudioController: IController? = null

    /**
     * 视频数据的管理
     */
    private var mVideoController: VideoController? = null

    /**
     * 音频采集编码默认配置
     */
    private var mAudioConfiguration = AudioConfiguration()

    /**
     * 视频编码默认配置
     */
    private var mVideoConfiguration = VideoConfiguration()

    private var mSender: Sender? = null
    private var statsListener: LiveStreamSession.StatsListener? = null
    private var statsWindowBytes = 0L
    private var statsWindowFrames = 0
    private var statsWindowStart = SystemClock.elapsedRealtime()

    private var mContext: Context? = null
    private var mTextureId = 0
    private var mEGLContext: EGLContext? = null
    private var audioSpecificConfig: ByteArray? = null


    /**
     * 设置音频编码和采集的参数
     */
    override fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        this.mAudioConfiguration = audioConfiguration
        mSender?.configureAudio(mAudioConfiguration, audioSpecificConfig)
    }

    /**
     * 设置视频的编码参数
     */
    override fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.mVideoConfiguration = videoConfiguration
        mSender?.configureVideo(mVideoConfiguration)
    }


    /**
     * 设置打包器
     */
    /**
     * 设置发送器
     */
    override fun setSender(sender: Sender) {
        this.mSender = sender
        sender.configureVideo(mVideoConfiguration)
        sender.configureAudio(mAudioConfiguration, audioSpecificConfig)
    }

    override fun setStatsListener(listener: LiveStreamSession.StatsListener?) {
        statsListener = listener
    }


    /**
     *  @see start 之前必须调用 prepare
     */
    override fun prepare(context: Context, textureId: Int, eglContext: EGLContext?) {
        this.mContext = context.applicationContext
        this.mTextureId = textureId
        this.mEGLContext = eglContext
        init()
    }

    private fun init() {
        mContext?.let { context ->
            mAudioController = AudioController(mAudioConfiguration)
            mVideoController =
                VideoController(context, mTextureId, mEGLContext, mVideoConfiguration)
            mAudioController?.setAudioDataListener(this)
            mVideoController?.setVideoDataListener(this)
            mWatermark?.let { watermark ->
                mVideoController?.setWatermark(watermark)
            }
            mSender?.configureVideo(mVideoConfiguration)
            mSender?.configureAudio(mAudioConfiguration, audioSpecificConfig)
        }
    }

    override fun start() {
        if (mSender == null) {
            LogHelper.w(TAG, "start ignored: sender not ready")
            return
        }
        if (mAudioController == null || mVideoController == null) {
            init()
        }
        resetStats()
        statsListener?.onVideoStats(0, mVideoConfiguration.fps)
        mAudioController?.start()
        mVideoController?.start()
    }

    override fun pause() {
        mAudioController?.pause()
        mVideoController?.pause()
    }

    override fun resume() {
        mAudioController?.resume()
        mVideoController?.resume()
    }

    override fun stop() {
        mAudioController?.stop()
        mVideoController?.stop()
        mAudioController = null
        mVideoController = null
        resetStats()
        statsListener?.onVideoStats(0, 0)
    }

    override fun setMute(isMute: Boolean) {
        mAudioController?.setMute(isMute)
    }

    override fun setVideoBps(bps: Int) {
        mVideoController?.setVideoBps(bps)
    }

    override fun onError(error: String?) {
        LogHelper.e(TAG, error)
    }

    /**
     * 音频编码之后的数据交于打包器处理
     */
    override fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
        mSender?.pushAudio(bb, bi)
    }

    /**
     * 音频输出格式
     */
    override fun onAudioOutformat(outputFormat: MediaFormat?) {
        val ascBuffer = outputFormat?.getByteBuffer("csd-0") ?: return
        val asc = ByteArray(ascBuffer.remaining())
        ascBuffer.get(asc)
        audioSpecificConfig = asc
        mSender?.configureAudio(mAudioConfiguration, audioSpecificConfig)
    }

    /**
     * 视频输出格式
     */
    override fun onVideoOutformat(outputFormat: MediaFormat?) {
    }

    /**
     * 视频编码数据交于打包
     */
    override fun onVideoData(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
        bi?.let {
            statsWindowBytes += it.size
            statsWindowFrames += 1
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - statsWindowStart
            if (elapsed >= 1000) {
                val bitrateKbps = ((statsWindowBytes * 8f) / elapsed).roundToInt()
                val fps = ((statsWindowFrames * 1000f) / elapsed).roundToInt()
                statsListener?.onVideoStats(bitrateKbps.coerceAtLeast(0), fps.coerceAtLeast(0))
                statsWindowBytes = 0
                statsWindowFrames = 0
                statsWindowStart = now
            }
        }
        if (bb != null && bi != null) {
            mSender?.pushVideo(bb, bi)
        }
    }

    override fun setWatermark(watermark: Watermark) {
        mWatermark = watermark
        mVideoController?.setWatermark(watermark)
    }

    private fun resetStats() {
        statsWindowBytes = 0
        statsWindowFrames = 0
        statsWindowStart = SystemClock.elapsedRealtime()
    }
}
