package com.astra.avpush.presentation.widget

import android.content.Context
import android.util.AttributeSet
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.nativebridge.NativeSender
import com.astra.avpush.stream.controller.LiveStreamSession
import com.astra.avpush.stream.controller.StreamController
import com.astrastream.avpush.R
import com.astra.avpush.unified.StreamError

data class LiveSessionConfig(
    val audio: AudioConfiguration,
    val video: VideoConfiguration,
    val camera: CameraConfiguration
)

class AVLiveView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : CameraView(context, attrs, defStyleAttr) {

    private var sessionConfig: LiveSessionConfig = buildInitialConfig(context, attrs)
    private var previewSizeListener: ((Int, Int) -> Unit)? = null
    private var cameraErrorListener: ((StreamError) -> Unit)? = null
    private var streamSession: LiveStreamSession = StreamController()

    init {
        attachCameraCallbacks()
    }

    fun configureSession(config: LiveSessionConfig) {
        sessionConfig = config
        streamSession.setAudioConfigure(config.audio)
        streamSession.setVideoConfigure(config.video)
    }

    fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        sessionConfig = sessionConfig.copy(audio = audioConfiguration)
        streamSession.setAudioConfigure(audioConfiguration)
    }

    fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        sessionConfig = sessionConfig.copy(video = videoConfiguration)
        streamSession.setVideoConfigure(videoConfiguration)
    }

    fun setCameraConfigure(cameraConfiguration: CameraConfiguration) {
        sessionConfig = sessionConfig.copy(camera = cameraConfiguration)
    }

    fun startPreview() {
        streamSession.setAudioConfigure(sessionConfig.audio)
        streamSession.setVideoConfigure(sessionConfig.video)
        startPreview(sessionConfig.camera)
    }

    override fun setWatermark(watermark: Watermark) {
        super.setWatermark(watermark)
        streamSession.setWatermark(watermark)
    }

    fun setSender(sender: NativeSender) {
        streamSession.setSender(sender)
    }

    fun setStatsListener(listener: LiveStreamSession.StatsListener) {
        streamSession.setStatsListener(listener)
    }

    fun setOnPreviewSizeListener(listener: (Int, Int) -> Unit) {
        previewSizeListener = listener
    }

    fun setOnCameraErrorListener(listener: (StreamError) -> Unit) {
        cameraErrorListener = listener
    }

    fun startLive() {
        streamSession.start()
    }

    fun pause() {
        streamSession.pause()
    }

    fun resume() {
        streamSession.resume()
    }

    fun stopLive() {
        streamSession.stop()
    }

    fun setMute(isMute: Boolean) {
        streamSession.setMute(isMute)
    }

    fun setVideoBps(bps: Int) {
        streamSession.setVideoBps(bps)
    }

    private fun buildInitialConfig(context: Context, attrs: AttributeSet?): LiveSessionConfig {
        var fps = 20
        var previewHeight = 1280
        var previewWidth = 720
        var sampleRate = 44100
        var backCamera = true
        var minRate = 400
        var maxRate = 1800

        if (attrs != null) {
            val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AVLiveView)
            fps = typedArray.getInteger(R.styleable.AVLiveView_fps, fps)
            previewHeight = typedArray.getInteger(R.styleable.AVLiveView_preview_height, previewHeight)
            previewWidth = typedArray.getInteger(R.styleable.AVLiveView_preview_width, previewWidth)
            sampleRate = typedArray.getInteger(R.styleable.AVLiveView_sampleRate, sampleRate)
            backCamera = typedArray.getBoolean(R.styleable.AVLiveView_back, backCamera)
            minRate = typedArray.getInteger(R.styleable.AVLiveView_videoMinRate, minRate)
            maxRate = typedArray.getInteger(R.styleable.AVLiveView_videoMaxRate, maxRate)
            typedArray.recycle()
        }

        val facing = if (backCamera) CameraConfiguration.Facing.BACK else CameraConfiguration.Facing.FRONT

        val audioConfig = AudioConfiguration(sampleRate = sampleRate, aec = true, mediaCodec = true)
        val videoConfig = VideoConfiguration(
            width = previewWidth,
            height = previewHeight,
            minBps = minRate,
            maxBps = maxRate,
            fps = fps,
            mediaCodec = true
        )
        val cameraConfig = CameraConfiguration(
            width = previewWidth,
            height = previewHeight,
            fps = fps,
            facing = facing
        )

        return LiveSessionConfig(audio = audioConfig, video = videoConfig, camera = cameraConfig)
    }

    private fun attachCameraCallbacks() {
        setCameraCallbacks(
            CameraCallbacks(
                onOpened = {
                    streamSession.prepare(context, getTextureId(), getEGLContext())
                },
                onPreviewSize = { width, height ->
                    previewSizeListener?.invoke(width, height)
                },
                onError = { error ->
                    cameraErrorListener?.invoke(error)
                }
            )
        )
    }
}
