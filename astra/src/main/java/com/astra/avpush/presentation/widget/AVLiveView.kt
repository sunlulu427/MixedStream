package com.astra.avpush.presentation.widget

import android.content.Context
import android.util.AttributeSet
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.camera.CameraRecorder
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.nativebridge.NativeSender
import com.astra.avpush.runtime.AstraLog
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

    private val logTag = "${javaClass.simpleName}-AV"
    private var sessionConfig: LiveSessionConfig = buildInitialConfig(context, attrs)
    private var previewSizeListener: ((Int, Int) -> Unit)? = null
    private var cameraErrorListener: ((StreamError) -> Unit)? = null
    private var statsListener: ((Int, Int) -> Unit)? = null
    private var activeSender: NativeSender? = null
    private var encoderRecorder: CameraRecorder? = null
    private var encoderWatermark: Watermark? = null
    private var streaming = false

    init {
        attachCameraCallbacks()
    }

    fun configureSession(config: LiveSessionConfig) {
        sessionConfig = config
        encoderRecorder?.prepare(config.video)
        configureNativeSession()
    }

    fun setAudioConfigure(audioConfiguration: AudioConfiguration) {
        sessionConfig = sessionConfig.copy(audio = audioConfiguration)
        configureNativeSession()
    }

    fun setVideoConfigure(videoConfiguration: VideoConfiguration) {
        sessionConfig = sessionConfig.copy(video = videoConfiguration)
        encoderRecorder?.prepare(videoConfiguration)
        configureNativeSession()
    }

    fun setCameraConfigure(cameraConfiguration: CameraConfiguration) {
        sessionConfig = sessionConfig.copy(camera = cameraConfiguration)
    }

    fun startPreview() {
        startPreview(sessionConfig.camera)
    }

    override fun setWatermark(watermark: Watermark) {
        super.setWatermark(watermark)
        encoderWatermark = watermark
        encoderRecorder?.setWatermark(watermark)
    }

    fun setSender(sender: NativeSender) {
        if (activeSender === sender) return
        activeSender = sender
        sender.setOnStatsListener { bitrate, fps ->
            statsListener?.invoke(bitrate, fps)
        }
        configureNativeSession()
    }

    fun setStatsListener(listener: ((Int, Int) -> Unit)?) {
        statsListener = listener
        activeSender?.setOnStatsListener { bitrate, fps ->
            statsListener?.invoke(bitrate, fps)
        }
    }

    fun setOnPreviewSizeListener(listener: (Int, Int) -> Unit) {
        previewSizeListener = listener
    }

    fun setOnCameraErrorListener(listener: (StreamError) -> Unit) {
        cameraErrorListener = listener
    }

    fun startLive() {
        if (streaming) {
            AstraLog.w(logTag, "startLive ignored: already streaming")
            return
        }
        val sender = activeSender ?: run {
            AstraLog.w(logTag, "startLive ignored: sender not set")
            return
        }
        val recorder = ensureRecorder() ?: run {
            AstraLog.w(logTag, "startLive ignored: recorder unavailable")
            return
        }
        configureNativeSession()
        val surface = sender.prepareVideoSurface(sessionConfig.video) ?: run {
            AstraLog.e(logTag, "Failed to obtain encoder surface from sender")
            return
        }
        recorder.prepare(sessionConfig.video)
        encoderWatermark?.let(recorder::setWatermark)
        runCatching {
            recorder.start(surface)
        }.onFailure { error ->
            AstraLog.e(logTag, error, "Failed to start encoder recorder")
            sender.releaseVideoSurface()
            return
        }
        sender.startSession()
        streaming = true
        statsListener?.invoke(0, sessionConfig.video.fps)
    }

    fun pause() {
        if (!streaming) return
        encoderRecorder?.pause()
        activeSender?.pauseSession()
    }

    fun resume() {
        if (!streaming) return
        encoderRecorder?.resume()
        activeSender?.resumeSession()
    }

    fun stopLive() {
        if (!streaming) {
            activeSender?.stopSession()
            return
        }
        streaming = false
        encoderRecorder?.stop()
        encoderRecorder = null
        activeSender?.stopSession()
        activeSender?.releaseVideoSurface()
        statsListener?.invoke(0, 0)
    }

    fun setMute(isMute: Boolean) {
        activeSender?.setMute(isMute)
    }

    fun setVideoBps(bps: Int) {
        sessionConfig = sessionConfig.copy(
            video = sessionConfig.video.withNewMaxBps(bps)
        )
        activeSender?.updateVideoBps(bps)
    }

    private fun configureNativeSession() {
        val sender = activeSender ?: return
        sender.configureSession(sessionConfig.audio, sessionConfig.video)
    }

    private fun ensureRecorder(): CameraRecorder? {
        val textureId = getTextureId()
        if (textureId <= 0) {
            AstraLog.w(logTag, "Encoder recorder not ready: invalid texture $textureId")
            return null
        }
        if (encoderRecorder == null) {
            encoderRecorder = CameraRecorder(textureId, getEGLContext()).apply {
                prepare(sessionConfig.video)
                encoderWatermark?.let(this::setWatermark)
            }
        }
        return encoderRecorder
    }

    override fun releaseCamera() {
        stopLive()
        super.releaseCamera()
    }

    private fun VideoConfiguration.withNewMaxBps(maxBps: Int): VideoConfiguration {
        return VideoConfiguration(
            width = width,
            height = height,
            fps = fps,
            maxBps = maxBps,
            minBps = minBps,
            ifi = ifi,
            mediaCodec = mediaCodec,
            codeType = codeType,
            codec = codec,
            mime = mime,
            spspps = spspps,
            surface = surface
        )
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
                    encoderRecorder?.stop()
                    encoderRecorder = null
                    configureNativeSession()
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
