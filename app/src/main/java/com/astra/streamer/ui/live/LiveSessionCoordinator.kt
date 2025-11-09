package com.astra.streamer.ui.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.core.content.ContextCompat
import com.astra.avpush.domain.OnConnectListener
import com.astra.avpush.domain.AudioConfiguration
import com.astra.avpush.domain.CameraConfiguration
import com.astra.avpush.domain.VideoConfiguration
import com.astra.avpush.infrastructure.camera.Watermark
import com.astra.avpush.infrastructure.stream.nativebridge.NativeSender
import com.astra.avpush.infrastructure.stream.nativebridge.NativeSenderFactory
import com.astra.avpush.presentation.widget.AVLiveView
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.unified.ProtocolDetector
import com.astra.avpush.unified.StreamError
import com.astra.avpush.unified.TransportProtocol
import com.astra.streamer.data.LivePreferencesStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LiveSessionCoordinator(
    private val context: Context,
    private val state: MutableState<LiveUiState>,
    private val audioConfiguration: AudioConfiguration,
    private val preferencesStore: LivePreferencesStore
) {

    private val tag = "LiveSessionCoordinator"

    val captureOptions = defaultCaptureOptions()
    val streamOptions = defaultStreamOptions()
    val encoderOptions = defaultEncoderOptions()
    private val safeCaptureOption = captureOptions.first()

    var liveView: AVLiveView? = null
    private var previewStarted = false
    private var previewRequested = false
    private var permissionWarningShown = false
    private var resumeStreamingWhenReady = false
    private var connectListener: OnConnectListener? = null
    private var activeProtocol: TransportProtocol? = null
    private var sender: NativeSender? = null

    fun onVideoStats(bitrateKbps: Int, fps: Int) {
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
        view.setStatsListener { bitrate, fps -> onVideoStats(bitrate, fps) }
        view.setOnPreviewSizeListener { width, height -> onCameraPreviewSize(width, height) }
        view.setOnCameraErrorListener { error -> onCameraError(error) }
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

    fun registerConnectListener(listener: OnConnectListener?) {
        connectListener = listener
        sender?.setOnConnectListener(listener)
    }

    fun startStreaming(onError: (String) -> Unit) {
        val snapshot = state.value
        val sanitizedUrl = snapshot.streamUrl.trim()
        if (sanitizedUrl.isBlank()) {
            onError("Stream URL cannot be empty")
            return
        }

        if (liveView == null) {
            onError("Preview surface not ready")
            return
        }

        if (sanitizedUrl != snapshot.streamUrl) {
            state.value = snapshot.copy(
                streamUrl = sanitizedUrl,
                pullUrls = StreamUrlFormatter.buildPullUrls(sanitizedUrl)
            )
            persistState()
        }

        val preparedSender = ensureSender(sanitizedUrl, onError) ?: return

        try {
            preparedSender.connect(sanitizedUrl)
        } catch (error: UnsatisfiedLinkError) {
            AstraLog.e(tag, error, "Sender not supported on this ABI")
            onError("Streaming is not supported on this device ABI. Please use an ARM device")
            releaseSender()
        } catch (throwable: Throwable) {
            AstraLog.e(tag, throwable, "Failed to connect sender")
            onError("Failed to initialise streaming module: ${throwable.message}")
            releaseSender()
        }
    }

    fun stopStreaming() {
        val shouldStopPreview = state.value.isStreaming || state.value.isConnecting
        if (shouldStopPreview) {
            liveView?.stopLive()
        }
        sender?.close()
        sender?.dispose()
        sender = null
        activeProtocol = null
    }

    fun releaseStreaming() {
        stopStreaming()
        releaseSender()
        connectListener = null
    }

    private fun ensureSender(
        streamUrl: String,
        onError: (String) -> Unit
    ): NativeSender? {
        val protocol = runCatching { ProtocolDetector.detectProtocol(streamUrl) }
            .getOrElse { error ->
                AstraLog.e(tag, "Failed to detect protocol for $streamUrl: ${error.message}")
                onError("Unsupported stream protocol")
                return null
            }

        releaseSender()

        val newSender = runCatching { NativeSenderFactory.createForProtocol(protocol) }
            .onFailure { error ->
                AstraLog.e(tag, error, "Failed to create sender for protocol=${protocol.displayName}")
                when (error) {
                    is UnsatisfiedLinkError -> onError("Streaming is not supported on this device ABI. Please use an ARM device")
                    else -> onError(error.message ?: "Failed to create streaming sender")
                }
            }
            .getOrNull()
            ?: return null

        sender = newSender
        activeProtocol = protocol
        connectListener?.let(newSender::setOnConnectListener)
        liveView?.let { view ->
            view.setSender(newSender)
            applyStreamConfiguration(view)
        }

        AstraLog.i(tag, "Sender initialised for protocol=${protocol.displayName}")

        return newSender
    }

    private fun releaseSender() {
        runCatching {
            sender?.close()
            sender?.dispose()
        }
        sender = null
        activeProtocol = null
    }

    fun startPreview() {
        if (previewStarted) return
        if (!hasCameraPermission()) {
            AstraLog.w(tag, "startPreview skipped: camera permission missing")
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
        val previousState = state.value
        state.value = previousState.copy(
            streamUrl = url,
            pullUrls = StreamUrlFormatter.buildPullUrls(url)
        )
        if (previousState.streamUrl != url) {
            if (!previousState.isStreaming) {
                releaseSender()
            } else {
                activeProtocol = null
            }
        }
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
        val previousState = state.value
        state.value = previousState.copy(
            streamUrl = clean,
            showUrlDialog = false,
            pullUrls = StreamUrlFormatter.buildPullUrls(clean)
        )
        if (previousState.streamUrl != clean) {
            if (!previousState.isStreaming) {
                releaseSender()
            } else {
                activeProtocol = null
            }
        }
        persistState()
        onValid()
    }

    private fun onCameraPreviewSize(width: Int, height: Int) {
        AstraLog.i(tag, "camera preview ready with ${width}x$height")
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

    private fun onCameraError(error: StreamError) {
        val message = error.message ?: error.code
        AstraLog.e(tag, "camera pipeline error [${error.code}]: $message")
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
        AstraLog.d(
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
        AstraLog.d(tag, "preview marked pending (permission or surface not ready)")
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
