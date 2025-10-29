package com.astra.avpush.presentation.widget

import android.content.Context
import android.util.AttributeSet
import com.astra.avpush.domain.callback.ICameraOpenListener
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.sender.Sender
import com.astra.avpush.stream.controller.LiveStreamSession
import com.astra.avpush.stream.controller.StreamController
import com.astrastream.avpush.R

class AVLiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CameraView(context, attrs, defStyleAttr), ICameraOpenListener {

    private var mFps = 20
    private var mPreviewWidth = 720
    private var mPreviewHeight = 1280
    private var mBack = true
    private var mSampleRate = 44100
    private var mVideoMinRate = 400
    private var mVideoMaxRate = 1800
    private var currentWatermark: Watermark? = null
    private var currentSender: Sender? = null
    private var statsListener: LiveStreamSession.StatsListener? = null
    private var previewSizeListener: ((Int, Int) -> Unit)? = null
    private var cameraErrorListener: ((String) -> Unit)? = null

    private var mVideoConfiguration = VideoConfiguration(
        width = mPreviewWidth,
        height = mPreviewHeight,
        minBps = mVideoMinRate,
        maxBps = mVideoMaxRate,
        fps = mFps,
        mediaCodec = true
    )
    private var mAudioConfiguration = AudioConfiguration(
        sampleRate = mSampleRate,
        aec = true,
        mediaCodec = true
    )
    private var mCameraConfiguration = CameraConfiguration(
        width = mPreviewWidth,
        height = mPreviewHeight,
        fps = mFps,
        facing = if (mBack) CameraConfiguration.Facing.BACK else CameraConfiguration.Facing.FRONT
    )
    private var streamSession: LiveStreamSession = StreamController()

    init {
        val typeArray = context.obtainStyledAttributes(attrs, R.styleable.AVLiveView)
        mFps = typeArray.getInteger(R.styleable.AVLiveView_fps, mFps)
        mPreviewHeight = typeArray.getInteger(R.styleable.AVLiveView_preview_height, mPreviewHeight)
        mPreviewWidth = typeArray.getInteger(R.styleable.AVLiveView_preview_width, mPreviewWidth)
        mSampleRate = typeArray.getInteger(R.styleable.AVLiveView_sampleRate, mSampleRate)
        mBack = typeArray.getBoolean(R.styleable.AVLiveView_back, mBack)
        mVideoMinRate = typeArray.getInteger(R.styleable.AVLiveView_videoMinRate, mVideoMinRate)
        mVideoMaxRate = typeArray.getInteger(R.styleable.AVLiveView_videoMaxRate, mVideoMaxRate)
        typeArray.recycle()

        //添加 Camera 打开的监听
        addCameraOpenCallback(this)
    }

    /**
     * 设置音频编码和采集的参数
     */
    fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        this.mAudioConfiguration = audioConfiguration
    }

    /**
     * 设置视频编码参数
     */
    fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        this.mVideoConfiguration = videoConfiguration
    }

    /**
     * 设置预览视频的参数
     */
    fun setCameraConfigure(cameraConfiguration: CameraConfiguration) {
        this.mCameraConfiguration = cameraConfiguration
    }

    /**
     * 开始预览
     */
    fun startPreview() {
        streamSession.setAudioConfigure(mAudioConfiguration)
        streamSession.setVideoConfigure(mVideoConfiguration)
        //开始预览
        startPreview(mCameraConfiguration)
    }


    override fun setWatermark(watermark: Watermark) {
        super.setWatermark(watermark)
        currentWatermark = watermark
        streamSession.setWatermark(watermark)
    }

    /**
     * 设置发送器
     */
    fun setSender(sender: Sender) {
        currentSender = sender
        streamSession.setSender(sender)
    }

    fun setStatsListener(listener: LiveStreamSession.StatsListener) {
        statsListener = listener
        streamSession.setStatsListener(listener)
    }

    fun setOnPreviewSizeListener(listener: (Int, Int) -> Unit) {
        previewSizeListener = listener
    }

    fun setOnCameraErrorListener(listener: (String) -> Unit) {
        cameraErrorListener = listener
    }

    /**
     * camera 打开可以初始化了
     */
    override fun onCameraOpen() {
        streamSession.prepare(context, getTextureId(), getEGLContext())
    }

    override fun onCameraError(message: String) {
        cameraErrorListener?.invoke(message)
    }

    override fun onCameraPreviewSizeSelected(width: Int, height: Int) {
        previewSizeListener?.invoke(width, height)
    }

    /**
     * 开始
     */
    fun startLive() {
        streamSession.start()
    }

    /**
     * 暂停
     */
    fun pause() {
        streamSession.pause()
    }

    /**
     * 恢复
     */
    fun resume() {
        streamSession.resume()
    }

    /**
     * 停止
     */
    fun stopLive() {
        streamSession.stop()
    }

    /**
     * 禁言
     */
    fun setMute(isMute: Boolean) {
        streamSession.setMute(isMute)
    }

    /**
     * 动态设置视频编码码率
     */
    fun setVideoBps(bps: Int) {
        streamSession.setVideoBps(bps)
    }

    /**
     * 注入自定义推流会话，便于测试或扩展不同策略实现。
     */
    fun attachStreamSession(session: LiveStreamSession) {
        streamSession = session
        streamSession.setAudioConfigure(mAudioConfiguration)
        streamSession.setVideoConfigure(mVideoConfiguration)
        currentSender?.let { streamSession.setSender(it) }
        currentWatermark?.let { streamSession.setWatermark(it) }
        statsListener?.let { streamSession.setStatsListener(it) }
    }
}
