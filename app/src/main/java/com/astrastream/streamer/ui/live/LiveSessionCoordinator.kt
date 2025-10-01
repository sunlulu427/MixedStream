package com.astrastream.streamer.ui.live

import android.content.Context
import android.graphics.Color
import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.runtime.MutableState
import androidx.core.content.ContextCompat
import com.astrastream.avpush.infrastructure.camera.Watermark
import com.astrastream.avpush.domain.config.AudioConfiguration
import com.astrastream.avpush.domain.config.CameraConfiguration
import com.astrastream.avpush.domain.config.VideoConfiguration
import com.astrastream.avpush.application.controller.LiveStreamSession
import com.astrastream.avpush.infrastructure.stream.packer.rtmp.RtmpPacker
import com.astrastream.avpush.infrastructure.stream.sender.rtmp.RtmpSender
import com.astrastream.avpush.core.utils.LogHelper
import com.astrastream.avpush.presentation.widget.AVLiveView
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class LiveSessionCoordinator(
    private val context: Context,
    private val state: MutableState<LiveUiState>,
    private val audioConfiguration: AudioConfiguration
) : LiveStreamSession.StatsListener {

    private val tag = "LiveSessionCoordinator"

    val captureOptions = defaultCaptureOptions()
    val streamOptions = defaultStreamOptions()
    val encoderOptions = defaultEncoderOptions()
    private val safeCaptureOption = captureOptions.first()

    var packer: RtmpPacker? = null
    var sender: RtmpSender? = null
    var liveView: AVLiveView? = null
    private var previewStarted = false
    private var previewRequested = false
    private var permissionWarningShown = false

    override fun onVideoStats(bitrateKbps: Int, fps: Int) {
        state.value = state.value.copy(currentBitrate = bitrateKbps, currentFps = fps)
    }

    fun attachLiveView(view: AVLiveView) {
        if (liveView === view) return
        liveView = view
        packer?.let(view::setPacker)
        sender?.let(view::setSender)
        view.setAudioConfigure(audioConfiguration)
        view.setStatsListener(this)
        view.setOnPreviewSizeListener { width, height -> onCameraPreviewSize(width, height) }
        view.setOnCameraErrorListener { message -> onCameraError(message) }
        applyStreamConfiguration(view)
        if (previewRequested) startPreview()
    }

    fun ensurePacker() {
        if (packer == null) {
            packer = RtmpPacker().also { current -> liveView?.setPacker(current) }
        }
    }

    fun ensureSender(onError: (String) -> Unit): Boolean {
        if (sender != null) return true
        return try {
            val newSender = RtmpSender()
            sender = newSender
            liveView?.setSender(newSender)
            true
        } catch (error: UnsatisfiedLinkError) {
            onError("当前设备架构不支持推流组件，请使用 ARM 真机")
            false
        } catch (t: Throwable) {
            onError("推流模块初始化失败: ${t.message}")
            false
        }
    }

    fun startPreview() {
        if (previewStarted) return
        if (!hasCameraPermission()) {
            LogHelper.w(tag, "startPreview skipped: camera permission missing")
            previewRequested = true
            if (!permissionWarningShown) {
                Toast.makeText(context, "请先授予相机权限", Toast.LENGTH_SHORT).show()
                permissionWarningShown = true
            }
            return
        }
        val view = liveView ?: run {
            previewRequested = true
            return
        }
        view.startPreview()
        previewStarted = true
        previewRequested = false
        permissionWarningShown = false
    }

    fun restartPreview() {
        liveView?.releaseCamera()
        previewStarted = false
        startPreview()
    }

    fun updateCapture(option: ResolutionOption) {
        if (state.value.captureResolution == option) return
        state.value = state.value.copy(captureResolution = option)
        applyStreamConfiguration()
        if (!state.value.isStreaming) restartPreview()
    }

    fun updateStream(option: ResolutionOption) {
        if (state.value.streamResolution == option) return
        state.value = state.value.copy(streamResolution = option)
        applyStreamConfiguration()
    }

    fun updateEncoder(option: EncoderOption) {
        if (state.value.encoder == option) return
        state.value = state.value.copy(encoder = option)
        applyStreamConfiguration()
    }

    fun updateBitrate(bitrate: Int) {
        val snapshot = state.value
        val clamped = bitrate.coerceIn(snapshot.minBitrate, snapshot.maxBitrate)
        state.value = snapshot.copy(targetBitrate = clamped)
        applyStreamConfiguration()
        if (state.value.isStreaming) liveView?.setVideoBps(clamped)
    }

    fun updateBitrateFromInput(text: String) {
        val parsed = text.filter { it.isDigit() }.toIntOrNull() ?: state.value.minBitrate
        updateBitrate(parsed)
    }

    fun updateStreamUrl(url: String) {
        state.value = state.value.copy(streamUrl = url)
    }

    fun togglePanel() {
        state.value = state.value.copy(showParameterPanel = !state.value.showParameterPanel)
    }

    fun setStatsVisible(visible: Boolean) {
        if (state.value.showStats != visible) state.value = state.value.copy(showStats = visible)
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
            Toast.makeText(context, "推流地址不能为空", Toast.LENGTH_SHORT).show()
            return
        }
        state.value = state.value.copy(streamUrl = clean, showUrlDialog = false)
        onValid()
    }

    private fun onCameraPreviewSize(width: Int, height: Int) {
        LogHelper.i(tag, "camera preview ready with ${width}x$height")
        val current = state.value
        if (current.captureResolution.width == width && current.captureResolution.height == height) {
            return
        }
        val updatedOption = captureOptions.find { it.width == width && it.height == height }
            ?: ResolutionOption(width, height, "${width} × ${height}")
        state.value = current.copy(captureResolution = updatedOption)
        applyStreamConfiguration()
    }

    private fun onCameraError(message: String) {
        LogHelper.e(tag, "camera pipeline error: $message")
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        state.value = state.value.copy(
            captureResolution = safeCaptureOption,
            isConnecting = false,
            isStreaming = false
        )
        applyStreamConfiguration()
        previewStarted = false
        previewRequested = false
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
                mediaCodec = adjusted.encoder.useHardware
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
        LogHelper.d(tag, "preview marked pending (permission or surface not ready)")
    }

    fun markStreamingStarted(targetBitrate: Int, fps: Int) {
        state.value = state.value.copy(
            isConnecting = false,
            isStreaming = true,
            currentBitrate = targetBitrate,
            currentFps = fps
        )
    }

    fun markStreamingStopped(fps: Int) {
        state.value = state.value.copy(
            isStreaming = false,
            isConnecting = false,
            currentBitrate = 0,
            currentFps = fps
        )
    }

    fun markConnecting() {
        state.value = state.value.copy(isConnecting = true)
    }

    fun clearConnecting() {
        state.value = state.value.copy(isConnecting = false)
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
            EncoderOption("硬件编解码 (MediaCodec)", true),
            EncoderOption("软件编解码", false)
        )
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}
