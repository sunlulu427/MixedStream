package com.astrastream.streamer.ui.unified

import android.content.Context
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.astra.avpush.unified.ConnectionQuality
import com.astra.avpush.unified.SessionState
import com.astra.avpush.unified.StreamEventListener
import com.astra.avpush.unified.StreamStats
import com.astra.avpush.unified.SurfaceProvider
import com.astra.avpush.unified.UnifiedStreamSession
import com.astra.avpush.unified.config.AudioConfig
import com.astra.avpush.unified.config.CameraConfig
import com.astra.avpush.unified.config.CameraFacing
import com.astra.avpush.unified.config.ExposureMode
import com.astra.avpush.unified.config.VideoConfig
import com.astra.avpush.unified.config.VideoProfile
import com.astra.avpush.unified.config.Watermark
import com.astra.avpush.unified.config.WhiteBalanceMode
import com.astra.avpush.unified.builder.createStreamSession
import com.astra.avpush.unified.config.AudioCodec
import com.astra.avpush.unified.config.RetryPolicy
import com.astra.avpush.unified.config.RtmpConfig
import com.astra.avpush.unified.config.VideoCodec
import com.astra.avpush.unified.error.StreamError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.Duration

/**
 * 统一推流会话协调器
 *
 * 负责管理统一API的推流会话和UI状态
 */
class UnifiedSessionCoordinator(
    private val context: Context,
    private val scope: CoroutineScope
) {

    // UI状态
    private val _state = mutableStateOf(UnifiedUiState())
    val state: MutableState<UnifiedUiState> = _state

    // 统一推流会话
    private var streamSession: UnifiedStreamSession? = null

    init {
        initializeSession()
    }

    private fun initializeSession() {
        streamSession = createStreamSession {
            // 视频配置
            video {
                width = 1280
                height = 720
                frameRate = 30
                bitrate = 2_000_000
                keyFrameInterval = 2
                codec = VideoCodec.H264
                profile = VideoProfile.BASELINE
                enableHardwareAcceleration = true
                enableAdaptiveBitrate = true
                minBitrate = 500_000
                maxBitrate = 4_000_000
            }

            // 音频配置
            audio {
                sampleRate = 44100
                bitrate = 128_000
                channels = 2
                codec = AudioCodec.AAC
                enableAEC = true
                enableAGC = true
                enableNoiseReduction = true
            }

            // 摄像头配置
            camera {
                facing = CameraFacing.BACK
                autoFocus = true
                stabilization = true
                exposureMode = ExposureMode.AUTO
                whiteBalanceMode = WhiteBalanceMode.AUTO
            }

            // RTMP传输配置
            addRtmp("rtmp://47.100.16.213:1935/live/123333") {
                connectTimeout = Duration.ofSeconds(10)
                retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 3)
                enableLowLatency = false
                enableTcpNoDelay = true
            }

            // 高级配置
            advanced {
                enableSimultaneousPush = false
                fallbackEnabled = true
                enableMetrics = true
                metricsInterval = Duration.ofSeconds(1)
                enableGpuAcceleration = true
                enableMemoryPool = true
            }
        }

        // 设置事件监听器
        streamSession?.setEventListener(object : StreamEventListener {
            override fun onSessionStateChanged(state: SessionState) {
                updateSessionState(state)
            }

            override fun onConnectionQualityChanged(quality: ConnectionQuality) {
                _state.value = _state.value.copy(connectionQuality = quality)
            }

            override fun onStatsUpdated(stats: StreamStats) {
                _state.value = _state.value.copy(streamStats = stats)
            }

            override fun onError(error: StreamError) {
                _state.value = _state.value.copy(
                    currentError = error.message,
                    isStreaming = false,
                    isConnecting = false
                )
            }
        })

        // 观察会话状态
        scope.launch {
            streamSession?.state?.collect { sessionState ->
                updateSessionState(sessionState)
            }
        }

        // 观察统计信息
        scope.launch {
            streamSession?.stats?.collect { stats ->
                _state.value = _state.value.copy(streamStats = stats)
            }
        }
    }

    private fun updateSessionState(sessionState: SessionState) {
        _state.value = when (sessionState) {
            SessionState.IDLE -> _state.value.copy(
                isStreaming = false,
                isConnecting = false,
                currentError = null
            )
            SessionState.PREPARING -> _state.value.copy(
                isConnecting = true,
                currentError = null
            )
            SessionState.PREPARED -> _state.value.copy(
                isConnecting = false,
                currentError = null
            )
            SessionState.STREAMING -> _state.value.copy(
                isStreaming = true,
                isConnecting = false,
                currentError = null
            )
            SessionState.STOPPING -> _state.value.copy(
                isConnecting = false
            )
            is SessionState.ERROR -> _state.value.copy(
                isStreaming = false,
                isConnecting = false,
                currentError = sessionState.error.message
            )
        }
    }

    fun updateStreamUrl(url: String) {
        _state.value = _state.value.copy(streamUrl = url)

        // 如果会话已经存在，需要重新配置传输
        streamSession?.let { session ->
            // 移除现有传输（在实际实现中需要跟踪传输ID）
            // 添加新的传输
            val newTransportId = session.addTransport(
                RtmpConfig(
                    pushUrl = url,
                    connectTimeout = Duration.ofSeconds(10),
                    retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 3)
                )
            )
            session.switchPrimaryTransport(newTransportId)
        }
    }

    fun toggleStreaming() {
        scope.launch {
            try {
                if (_state.value.isStreaming) {
                    streamSession?.stop()
                } else {
                    // 准备会话
                    streamSession?.prepare(context, object : SurfaceProvider {
                        override fun getPreviewSurface(): android.view.Surface? {
                            // 这里需要返回实际的预览Surface
                            // 在完整实现中，需要与AVLiveView集成
                            return null
                        }
                    })
                    streamSession?.start()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    currentError = "操作失败: ${e.message}",
                    isStreaming = false,
                    isConnecting = false
                )
            }
        }
    }

    fun switchCamera() {
        val newFacing = if (_state.value.cameraFacing == CameraFacing.BACK) {
            CameraFacing.FRONT
        } else {
            CameraFacing.BACK
        }

        _state.value = _state.value.copy(cameraFacing = newFacing)

        val newCameraConfig = CameraConfig(
            facing = newFacing,
            autoFocus = true,
            stabilization = true
        )

        // 在完整实现中，需要支持动态切换摄像头
        // streamSession?.updateCameraConfig(newCameraConfig)
    }

    fun toggleAudio() {
        val audioEnabled = !_state.value.audioEnabled
        _state.value = _state.value.copy(audioEnabled = audioEnabled)

        val newAudioConfig = AudioConfig(
            sampleRate = 44100,
            bitrate = if (audioEnabled) 128_000 else 0,
            channels = 2,
            codec = AudioCodec.AAC,
            enableAEC = true,
            enableAGC = true,
            enableNoiseReduction = true
        )

        streamSession?.updateAudioConfig(newAudioConfig)
    }

    fun adjustBitrate() {
        val currentBitrate = _state.value.videoBitrate
        val newBitrate = when (currentBitrate) {
            500_000 -> 1_000_000
            1_000_000 -> 2_000_000
            2_000_000 -> 4_000_000
            else -> 500_000
        }

        _state.value = _state.value.copy(videoBitrate = newBitrate)

        val newVideoConfig = VideoConfig(
            width = 1280,
            height = 720,
            frameRate = 30,
            bitrate = newBitrate,
            keyFrameInterval = 2,
            codec = VideoCodec.H264,
            enableHardwareAcceleration = true
        )

        streamSession?.updateVideoConfig(newVideoConfig)
    }

    fun setWatermark(watermark: Watermark) {
        streamSession?.setWatermark(watermark)
    }

    fun toggleStatsPanel() {
        _state.value = _state.value.copy(showStatsPanel = !_state.value.showStatsPanel)
    }

    fun dismissError() {
        _state.value = _state.value.copy(currentError = null)
    }

    fun release() {
        scope.launch {
            streamSession?.release()
        }
    }
}

/**
 * 统一API的UI状态
 */
data class UnifiedUiState(
    val isStreaming: Boolean = false,
    val isConnecting: Boolean = false,
    val streamUrl: String = "rtmp://47.100.16.213:1935/live/123333",
    val connectionQuality: ConnectionQuality = ConnectionQuality.POOR,
    val streamStats: StreamStats? = null,
    val showStatsPanel: Boolean = false,
    val currentError: String? = null,
    val videoBitrate: Int = 2_000_000,
    val videoResolution: String = "1280x720",
    val audioEnabled: Boolean = true,
    val cameraFacing: CameraFacing = CameraFacing.BACK
)