package com.devyk.av.rtmp.library.controller

import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import com.devyk.av.rtmp.library.callback.IController
import com.devyk.av.rtmp.library.camera.Watermark
import com.devyk.av.rtmp.library.config.AudioConfiguration
import com.devyk.av.rtmp.library.config.VideoConfiguration
import com.devyk.av.rtmp.library.stream.PacketType
import com.devyk.av.rtmp.library.stream.packer.Packer
import com.devyk.av.rtmp.library.stream.sender.Sender
import com.devyk.av.rtmp.library.utils.LogHelper
import java.nio.ByteBuffer
import javax.microedition.khronos.egl.EGLContext
import kotlin.math.roundToInt

/**
 * <pre>
 *     author  : devyk on 2020-07-15 22:05
 *     blog    : https://juejin.im/user/578259398ac2470061f3a3fb/posts
 *     github  : https://github.com/yangkun19921001
 *     mailbox : yang1001yk@gmail.com
 *     desc    : This is StreamController
 * </pre>
 */
class StreamController : LiveStreamSession, IController.OnAudioDataListener,
    IController.OnVideoDataListener, Packer.OnPacketListener {
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

    /**
     * 打包器
     */
    private var mPacker: Packer? = null

    /**
     * 发送器
     */
    private var mSender: Sender? = null
    private var statsListener: LiveStreamSession.StatsListener? = null
    private var statsWindowBytes = 0L
    private var statsWindowFrames = 0
    private var statsWindowStart = SystemClock.elapsedRealtime()

    private var mContext: Context? = null
    private var mTextureId = 0
    private var mEGLContext: EGLContext? = null


    /**
     * 设置音频编码和采集的参数
     */
    override fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        this.mAudioConfiguration = audioConfiguration
    }

    /**
     * 设置视频的编码参数
     */
    override fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.mVideoConfiguration = videoConfiguration
    }


    /**
     * 设置打包器
     */
    override fun setPacker(packer: Packer) {
        this.mPacker = packer
    }

    /**
     * 设置发送器
     */
    override fun setSender(sender: Sender) {
        this.mSender = sender
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
            mPacker?.setPacketListener(this)
            mAudioController?.setAudioDataListener(this)
            mVideoController?.setVideoDataListener(this)
            mWatermark?.let { watermark ->
                mVideoController?.setWatermark(watermark)
            }
        }
    }

    override fun start() {
        if (mPacker == null || mSender == null) {
            LogHelper.w(TAG, "start ignored: packer or sender not ready")
            return
        }
        if (mAudioController == null || mVideoController == null)
            init()
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
        mPacker?.onAudioData(bb, bi)
    }

    /**
     * 音频输出格式
     */
    override fun onAudioOutformat(outputFormat: MediaFormat?) {
    }

    /**
     * 视频输出格式
     */
    override fun onVideoOutformat(outputFormat: MediaFormat?) {
        val spsb = outputFormat?.getByteBuffer("csd-0") ?: return
        val sps = ByteArray(spsb.remaining())
        spsb.get(sps, 0, sps.size)
        val ppsb = outputFormat.getByteBuffer("csd-1") ?: return
        val pps = ByteArray(ppsb.remaining())
        ppsb.get(pps, 0, pps.size)
        mPacker?.onVideoSpsPpsData(sps, pps, PacketType.SPS_PPS)
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
        mPacker?.onVideoData(bb, bi)
    }

    /**
     * 打包完成的数据，准备发送
     */
    override fun onPacket(byteArray: ByteArray, packetType: PacketType) {
        mSender?.onData(byteArray, packetType)
    }

    override fun onPacket(sps: ByteArray?, pps: ByteArray?, packetType: PacketType) {
        if (sps == null || pps == null) {
            LogHelper.w(TAG, "drop video config packet: missing sps/pps")
            return
        }
        mSender?.onData(sps, pps, packetType)
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
