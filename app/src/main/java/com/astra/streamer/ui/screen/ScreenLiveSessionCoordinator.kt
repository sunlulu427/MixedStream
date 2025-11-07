package com.astra.streamer.ui.screen

import android.app.Activity
import android.content.Context
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.widget.Toast
import androidx.compose.runtime.MutableState
import com.astra.avpush.domain.callback.OnConnectListener
import com.astra.avpush.domain.config.AudioConfiguration
import com.astra.avpush.domain.config.ScreenCaptureConfiguration
import com.astra.avpush.domain.config.VideoConfiguration
import com.astra.avpush.infrastructure.stream.sender.rtmp.RtmpStreamSession
import com.astra.avpush.runtime.AstraLog
import com.astra.avpush.stream.controller.LiveStreamSession
import com.astra.avpush.stream.controller.ScreenStreamController

class ScreenLiveSessionCoordinator(
    private val activity: Activity,
    private val state: MutableState<ScreenLiveUiState>
) : LiveStreamSession.StatsListener {

    private val tag = "ScreenLiveCoordinator"
    private val appContext: Context = activity.applicationContext

    private val displayMetrics: DisplayMetrics = activity.resources.displayMetrics

    private val session: ScreenStreamController = ScreenStreamController().also {
        it.setStatsListener(this)
    }

    var sender: RtmpStreamSession? = null
        private set

    private var mediaProjection: MediaProjection? = null

    private var overlayObserver: ((ScreenLiveUiState) -> Unit)? = null

    private var audioConfiguration = AudioConfiguration(
        sampleRate = 48000,
        channelCount = 2,
        audioSource = MediaRecorder.AudioSource.MIC,
        mediaCodec = true
    )

    private var videoConfiguration = VideoConfiguration(
        width = displayMetrics.widthPixels,
        height = displayMetrics.heightPixels,
        fps = 30,
        maxBps = state.value.targetBitrate * 2,
        minBps = (state.value.targetBitrate * 0.7f).toInt()
    )

    init {
        session.setAudioConfigure(audioConfiguration)
        session.setVideoConfigure(videoConfiguration)
    }

    override fun onVideoStats(bitrateKbps: Int, fps: Int) {
        state.value = state.value.copy(currentBitrate = bitrateKbps, currentFps = fps)
        notifyStateChanged()
    }

    fun attachProjection(projection: MediaProjection) {
        mediaProjection = projection
        updateScreenCaptureConfiguration()
        session.prepare(appContext, 0, null)
        state.value = state.value.copy(projectionReady = true)
        notifyStateChanged()
        Toast.makeText(activity, "Screen capture ready", Toast.LENGTH_SHORT).show()
    }

    fun ensureSender(onError: (String) -> Unit): Boolean {
        if (sender != null) return true
        return try {
            val newSender = RtmpStreamSession()
            sender = newSender
            session.setSender(newSender)
            true
        } catch (error: UnsatisfiedLinkError) {
            AstraLog.e(tag, error, "Sender not supported")
            onError("Streaming not supported on this ABI")
            false
        } catch (throwable: Throwable) {
            AstraLog.e(tag, throwable, "Failed to create sender")
            onError("Failed to initialise sender: ${throwable.message}")
            false
        }
    }

    fun startStreaming(onMissingProjection: () -> Unit): Boolean {
        if (state.value.streamUrl.isBlank()) {
            Toast.makeText(activity, "Publish URL required", Toast.LENGTH_SHORT).show()
            return false
        }
        if (mediaProjection == null) {
            onMissingProjection()
            return false
        }
        state.value = state.value.copy(isConnecting = true)
        notifyStateChanged()
        session.setVideoBps(state.value.targetBitrate)
        return true
    }

    fun stopStreaming() {
        session.stop()
        sender?.close()
        state.value = state.value.copy(
            isStreaming = false,
            isConnecting = false,
            currentBitrate = 0,
            currentFps = 0
        )
        notifyStateChanged()
    }

    fun confirmConnected(targetBitrate: Int, fps: Int) {
        session.start()
        state.value = state.value.copy(
            isStreaming = true,
            isConnecting = false,
            currentBitrate = targetBitrate,
            currentFps = fps
        )
        notifyStateChanged()
    }

    fun markConnectionFailed() {
        state.value = state.value.copy(isConnecting = false, isStreaming = false)
        notifyStateChanged()
    }

    fun updateStreamUrl(url: String) {
        state.value = state.value.copy(streamUrl = url)
        notifyStateChanged()
    }

    fun updateBitrate(target: Int) {
        val sanitized = target.coerceIn(500, 6000)
        state.value = state.value.copy(targetBitrate = sanitized)
        notifyStateChanged()
        videoConfiguration = VideoConfiguration(
            width = videoConfiguration.width,
            height = videoConfiguration.height,
            fps = videoConfiguration.fps,
            maxBps = (sanitized * 1.5f).toInt(),
            minBps = (sanitized * 0.7f).toInt(),
            ifi = videoConfiguration.ifi,
            mediaCodec = videoConfiguration.mediaCodec,
            codeType = videoConfiguration.codeType,
            codec = videoConfiguration.codec,
            mime = videoConfiguration.mime,
            spspps = videoConfiguration.spspps,
            surface = videoConfiguration.surface
        )
        session.setVideoConfigure(videoConfiguration)
    }

    fun toggleMic(include: Boolean) {
        state.value = state.value.copy(includeMic = include)
        notifyStateChanged()
        updateScreenCaptureConfiguration()
    }

    fun togglePlayback(include: Boolean) {
        state.value = state.value.copy(includePlayback = include)
        notifyStateChanged()
        updateScreenCaptureConfiguration()
    }

    fun toggleStats(visible: Boolean) {
        state.value = state.value.copy(showStats = visible)
        notifyStateChanged()
    }

    fun release() {
        session.stop()
        sender?.close()
        sender = null
        mediaProjection?.stop()
        mediaProjection = null
        notifyStateChanged()
    }

    fun attachConnectListener(listener: OnConnectListener) {
        sender?.setOnConnectListener(listener)
    }

    fun detachConnectListener() {
        sender?.setOnConnectListener(null)
    }

    fun configuredFps(): Int = videoConfiguration.fps

    fun setOverlayObserver(observer: (ScreenLiveUiState) -> Unit) {
        overlayObserver = observer
        observer(state.value)
    }

    private fun updateScreenCaptureConfiguration() {
        val projection = mediaProjection ?: return
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        val label = "${width} × ${height}"
        state.value = state.value.copy(resolutionLabel = label)
        notifyStateChanged()
        val config = ScreenCaptureConfiguration(
            width = width,
            height = height,
            densityDpi = displayMetrics.densityDpi,
            fps = videoConfiguration.fps,
            includeMic = state.value.includeMic,
            includePlayback = state.value.includePlayback
        )
        session.setScreenCapture(projection, config)
    }

    private fun notifyStateChanged() {
        overlayObserver?.invoke(state.value)
    }
}
