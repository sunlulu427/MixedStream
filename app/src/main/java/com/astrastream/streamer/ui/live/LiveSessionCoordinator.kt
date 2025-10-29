package com.astrastream.streamer.ui.live

import android.content.Context
import android.graphics.Color
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.core.content.ContextCompat
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.CameraConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.stream.controller.LiveStreamSession
import com.astra.avpush.unified.config.CameraFacing
import com.astra.avpush.infrastructure.stream.sender.rtmp.RtmpSender
import com.astra.avpush.unified.UnifiedStreamSession
import com.astra.avpush.unified.builder.createStreamSession
import com.astra.avpush.unified.config.VideoCodec
import com.astra.avpush.unified.config.AudioCodec
import com.astra.avpush.runtime.LogHelper
import com.astra.avpush.presentation.widget.AVLiveView
import com.astrastream.streamer.data.LivePreferencesStore
import com.astrastream.streamer.ui.live.StreamUrlFormatter.buildPullUrls
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LiveSessionCoordinator(
    private val context: Context,
    private val state: MutableState<LiveUiState>,
    private val audioConfiguration: AudioConfiguration,
    private val preferencesStore: LivePreferencesStore
) : LiveStreamSession.StatsListener {

    private val tag = "LiveSessionCoordinator"

    val captureOptions = defaultCaptureOptions()
    val streamOptions = defaultStreamOptions()
    val encoderOptions = defaultEncoderOptions()
    private val safeCaptureOption = captureOptions.first()

    var sender: RtmpSender? = null
    var unifiedSession: UnifiedStreamSession? = null
    var liveView: AVLiveView? = null
    private var previewStarted = false
    private var previewRequested = false
    private var permissionWarningShown = false
    private var resumeStreamingWhenReady = false
    var useUnifiedApi = false
        private set

    override fun onVideoStats(bitrateKbps: Int, fps: Int) {
        state.value = state.value.copy(currentBitrate = bitrateKbps, currentFps = fps)
    }

    fun attachLiveView(view: AVLiveView) {
        if (liveView === view) return
        val previousView = liveView
        val wasStreaming = state.value.isStreaming
        liveView = view
        previousView?.let {
            it.stopLive()
            it.releaseCamera()
        }
        sender?.let(view::setSender)
        view.setAudioConfigure(audioConfiguration)
        view.setStatsListener(this)
        view.setOnPreviewSizeListener { width, height -> onCameraPreviewSize(width, height) }
        view.setOnCameraErrorListener { message -> onCameraError(message) }
        applyStreamConfiguration(view)
        when {
            previewRequested -> startPreview()
            previewStarted -> {
                previewStarted = false
                state.value = state.value.copy(previewReady = false, cameraError = null)
                startPreview()
            }
        }
        resumeStreamingWhenReady = wasStreaming
    }

    fun ensureSender(onError: (String) -> Unit): Boolean {
        val streamUrl = state.value.streamUrl

        // 检测是否应该使用统一API（基于URL协议）
        useUnifiedApi = shouldUseUnifiedApi(streamUrl)

        if (useUnifiedApi) {
            return ensureUnifiedSession(onError)
        } else {
            return ensureLegacySender(onError)
        }
    }

    private fun shouldUseUnifiedApi(url: String): Boolean {
        // 目前仅对非RTMP协议使用统一API，保持RTMP使用传统实现
        // 未来可以通过配置开关来决定RTMP是否也使用统一API
        return url.startsWith("webrtc://", ignoreCase = true) ||
               url.startsWith("wss://", ignoreCase = true) ||
               url.startsWith("ws://", ignoreCase = true) ||
               url.startsWith("srt://", ignoreCase = true) ||
               url.contains("webrtc", ignoreCase = true)

        // 如果需要RTMP也使用统一API，可以添加：
        // || url.startsWith("rtmp://", ignoreCase = true) ||
        // || url.startsWith("rtmps://", ignoreCase = true)
    }

    private fun ensureUnifiedSession(onError: (String) -> Unit): Boolean {
        if (unifiedSession != null) return true
        return try {
            val currentState = state.value
            val session = createStreamSession {
                video {
                    width = currentState.captureResolution.width
                    height = currentState.captureResolution.height
                    frameRate = 30
                    bitrate = currentState.targetBitrate * 1000
                    codec = VideoCodec.H264
                }

                audio {
                    sampleRate = 44100
                    bitrate = 128_000
                    channels = 2
                    codec = AudioCodec.AAC
                }

                camera {
                    facing = CameraFacing.BACK
                    autoFocus = true
                    stabilization = true
                }

                // 使用协议透明的addStream方法
                addStream(currentState.streamUrl)
            }

            unifiedSession = session
            LogHelper.i(tag, "Unified streaming session created for URL: ${currentState.streamUrl}")
            true
        } catch (t: Throwable) {
            LogHelper.e(tag, "Failed to create unified session: ${t.message}")
            onError("Failed to initialise unified streaming: ${t.message}")
            false
        }
    }

    private fun ensureLegacySender(onError: (String) -> Unit): Boolean {
        if (sender != null) return true
        return try {
            val newSender = RtmpSender()
            sender = newSender
            liveView?.setSender(newSender)
            true
        } catch (error: UnsatisfiedLinkError) {
            onError("Streaming is not supported on this device ABI. Please use an ARM device")
            false
        } catch (t: Throwable) {
            onError("Failed to initialise streaming module: ${t.message}")
            false
        }
    }

    fun startPreview() {
        if (previewStarted) return
        if (!hasCameraPermission()) {
            LogHelper.w(tag, "startPreview skipped: camera permission missing")
            previewRequested = true
            state.value = state.value.copy(previewReady = false, cameraError = null)
            if (!permissionWarningShown) {
                Toast.makeText(context, "Camera permission required", Toast.LENGTH_SHORT).show()
                permissionWarningShown = true
            }
            return
        }
        val view = liveView ?: run {
            previewRequested = true
            state.value = state.value.copy(previewReady = false, cameraError = null)
            return
        }
        view.startPreview()
        previewStarted = true
        previewRequested = false
        permissionWarningShown = false
        state.value = state.value.copy(previewReady = true, cameraError = null)
    }

    fun restartPreview() {
        liveView?.releaseCamera()
        previewStarted = false
        state.value = state.value.copy(previewReady = false, cameraError = null)
        startPreview()
    }

    fun updateCapture(option: ResolutionOption) {
        if (state.value.captureResolution == option) return
        state.value = state.value.copy(captureResolution = option)
        applyStreamConfiguration()
        persistState()
        if (!state.value.isStreaming) restartPreview()
    }

    fun updateStream(option: ResolutionOption) {
        if (state.value.streamResolution == option) return
        state.value = state.value.copy(streamResolution = option)
        applyStreamConfiguration()
        persistState()
    }

    fun updateEncoder(option: EncoderOption) {
        if (state.value.encoder == option) return
        state.value = state.value.copy(encoder = option)
        applyStreamConfiguration()
        persistState()
    }

    fun updateBitrate(bitrate: Int) {
        val snapshot = state.value
        val clamped = bitrate.coerceIn(snapshot.minBitrate, snapshot.maxBitrate)
        state.value = snapshot.copy(targetBitrate = clamped)
        applyStreamConfiguration()
        persistState()
        if (state.value.isStreaming) liveView?.setVideoBps(clamped)
    }

    fun updateBitrateFromInput(text: String) {
        val parsed = text.filter { it.isDigit() }.toIntOrNull() ?: state.value.minBitrate
        updateBitrate(parsed)
    }

    fun updateStreamUrl(url: String) {
        state.value = state.value.copy(streamUrl = url, pullUrls = buildPullUrls(url))
        persistState()
    }

    fun togglePanel() {
        state.value = state.value.copy(showParameterPanel = !state.value.showParameterPanel)
        persistState()
    }

    fun setStatsVisible(visible: Boolean) {
        if (state.value.showStats != visible) {
            state.value = state.value.copy(showStats = visible)
            persistState()
        }
    }

    fun showUrlDialog() {
        state.value = state.value.copy(showUrlDialog = true)
    }

    fun hideUrlDialog() {
        state.value = state.value.copy(showUrlDialog = false)
    }

    fun confirmUrl(url: String, onValid: () -> Unit) {
        val clean = url.trim()
        if (clean.isEmpty()) {
            Toast.makeText(context, "Publish URL cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }
        state.value = state.value.copy(
            streamUrl = clean,
            showUrlDialog = false,
            pullUrls = buildPullUrls(clean)
        )
        persistState()
        onValid()
    }

    private fun onCameraPreviewSize(width: Int, height: Int) {
        LogHelper.i(tag, "camera preview ready with ${width}x$height")
        val current = state.value
        if (current.captureResolution.width == width && current.captureResolution.height == height) {
            if (!current.previewReady) {
                state.value = current.copy(previewReady = true, cameraError = null)
            }
            if (resumeStreamingWhenReady && state.value.isStreaming) {
                liveView?.startLive()
                liveView?.setVideoBps(state.value.targetBitrate)
                resumeStreamingWhenReady = false
            }
            return
        }
        val updatedOption = captureOptions.find { it.width == width && it.height == height }
            ?: ResolutionOption(width, height, "${width} × ${height}")
        state.value = current.copy(
            captureResolution = updatedOption,
            previewReady = true,
            cameraError = null
        )
        applyStreamConfiguration()
        persistState()
        if (resumeStreamingWhenReady && state.value.isStreaming) {
            liveView?.startLive()
            liveView?.setVideoBps(state.value.targetBitrate)
            resumeStreamingWhenReady = false
        }
    }

    private fun onCameraError(message: String) {
        LogHelper.e(tag, "camera pipeline error: $message")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        state.value = state.value.copy(
            captureResolution = safeCaptureOption,
            isConnecting = false,
            isStreaming = false,
            previewReady = false,
            cameraError = message
        )
        applyStreamConfiguration()
        previewStarted = false
        previewRequested = false
        persistState()
    }

    fun applyStreamConfiguration(target: AVLiveView? = liveView) {
        val current = state.value
        val minBps = max(300, (current.targetBitrate * 0.7f).roundToInt())
        val maxBps = max(minBps + 200, (current.targetBitrate * 1.3f).roundToInt())
        val adjusted = current.copy(
            minBitrate = minBps,
            maxBitrate = maxBps,
            targetBitrate = current.targetBitrate.coerceIn(minBps, maxBps)
        )
        if (adjusted != current) state.value = adjusted
        LogHelper.d(
            tag,
            "applyStreamConfiguration capture=${adjusted.captureResolution.width}x${adjusted.captureResolution.height}, stream=${adjusted.streamResolution.width}x${adjusted.streamResolution.height}, fps=${adjusted.videoFps}"
        )
        val view = target ?: return
        view.setVideoConfigure(
            VideoConfiguration(
                width = adjusted.streamResolution.width,
                height = adjusted.streamResolution.height,
                fps = adjusted.videoFps,
                maxBps = adjusted.maxBitrate,
                minBps = adjusted.minBitrate,
                ifi = adjusted.gop,
                mediaCodec = adjusted.encoder.useHardware,
                codec = adjusted.encoder.videoCodec
            )
        )
        view.setCameraConfigure(
            CameraConfiguration(
                width = adjusted.captureResolution.width,
                height = adjusted.captureResolution.height,
                fps = adjusted.videoFps
            )
        )
        if (adjusted.isStreaming) view.setVideoBps(adjusted.targetBitrate)
        updateWatermark(view, adjusted)
    }

    private fun updateWatermark(target: AVLiveView, state: LiveUiState) {
        val base = min(state.captureResolution.width, state.captureResolution.height)
        val textSize = (base * 0.05f).roundToInt().coerceIn(24, 96)
        target.setWatermark(Watermark("Mato", Color.WHITE, textSize, null))
    }

    fun markPreviewPending() {
        previewStarted = false
        previewRequested = true
        state.value = state.value.copy(previewReady = false, cameraError = null)
        LogHelper.d(tag, "preview marked pending (permission or surface not ready)")
    }

    fun markStreamingStarted(targetBitrate: Int, fps: Int) {
        state.value = state.value.copy(
            isConnecting = false,
            isStreaming = true,
            currentBitrate = targetBitrate,
            currentFps = fps,
            showParameterPanel = false
        )
        resumeStreamingWhenReady = false
    }

    fun markStreamingStopped(fps: Int) {
        state.value = state.value.copy(
            isStreaming = false,
            isConnecting = false,
            currentBitrate = 0,
            currentFps = fps
        )
        resumeStreamingWhenReady = false
    }

    fun markConnecting() {
        state.value = state.value.copy(isConnecting = true)
    }

    fun clearConnecting() {
        state.value = state.value.copy(isConnecting = false)
    }

    fun isPreviewReady(): Boolean = previewStarted

    private fun persistState() {
        preferencesStore.save(state.value)
    }

    companion object {
        fun defaultCaptureOptions() = listOf(
            ResolutionOption(720, 1280, "720 × 1280"),
            ResolutionOption(960, 1920, "960 × 1920"),
            ResolutionOption(1080, 1920, "1080 × 1920")
        )

        fun defaultStreamOptions() = listOf(
            ResolutionOption(540, 960, "540 × 960"),
            ResolutionOption(720, 1280, "720 × 1280"),
            ResolutionOption(960, 1920, "960 × 1920")
        )

        fun defaultEncoderOptions() = listOf(
            EncoderOption("H.264", true, VideoConfiguration.VideoCodec.H264, "硬件 H.264 编码"),
            EncoderOption("H.265", true, VideoConfiguration.VideoCodec.H265, "硬件 H.265 编码"),
            EncoderOption("软件", false, VideoConfiguration.VideoCodec.H264, "软件编解码")
        )
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
