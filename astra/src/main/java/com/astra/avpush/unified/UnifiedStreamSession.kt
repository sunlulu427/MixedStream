package com.astra.avpush.unified

import android.content.Context
import android.view.Surface
import com.astra.avpush.unified.config.AudioConfig
import com.astra.avpush.unified.config.TransportConfig
import com.astra.avpush.unified.config.TransportId
import com.astra.avpush.unified.config.TransportProtocol
import com.astra.avpush.unified.config.VideoConfig
import com.astra.avpush.unified.config.Watermark
import com.astra.avpush.unified.error.StreamError
import com.astra.avpush.unified.error.TransportError
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.time.Duration

/**
 * 统一推流会话接口
 *
 * 提供协议无关的推流功能，支持多协议并发推流、智能切换和自适应码率等高级功能。
 */
interface UnifiedStreamSession {

    /**
     * 会话状态流
     */
    val state: StateFlow<SessionState>

    /**
     * 推流统计信息流
     */
    val stats: StateFlow<StreamStats>

    /**
     * 准备推流会话
     *
     * @param context Android上下文
     * @param surfaceProvider 预览Surface提供者
     */
    suspend fun prepare(context: Context, surfaceProvider: SurfaceProvider)

    /**
     * 开始推流
     */
    suspend fun start()

    /**
     * 停止推流
     */
    suspend fun stop()

    /**
     * 释放资源
     */
    suspend fun release()

    /**
     * 添加传输协议
     *
     * @param config 传输配置
     * @return 传输ID
     */
    fun addTransport(config: TransportConfig): TransportId

    /**
     * 移除传输协议
     *
     * @param transportId 传输ID
     */
    fun removeTransport(transportId: TransportId)

    /**
     * 切换主要传输协议
     *
     * @param transportId 新的主传输ID
     */
    fun switchPrimaryTransport(transportId: TransportId)

    /**
     * 动态更新视频配置
     *
     * @param config 新的视频配置
     */
    fun updateVideoConfig(config: VideoConfig)

    /**
     * 动态更新音频配置
     *
     * @param config 新的音频配置
     */
    fun updateAudioConfig(config: AudioConfig)

    /**
     * 观察指定传输的状态
     *
     * @param transportId 传输ID
     * @return 传输状态流
     */
    fun observeTransportState(transportId: TransportId): Flow<TransportState>

    /**
     * 观察连接质量
     *
     * @return 连接质量流
     */
    fun observeConnectionQuality(): Flow<ConnectionQuality>

    /**
     * 获取所有传输的统计信息
     *
     * @return 传输统计信息映射流
     */
    fun getAllTransportStats(): Flow<Map<TransportId, TransportStats>>

    /**
     * 设置水印
     *
     * @param watermark 水印配置
     */
    fun setWatermark(watermark: Watermark)

    /**
     * 设置事件监听器
     *
     * @param listener 事件监听器
     */
    fun setEventListener(listener: StreamEventListener)
}

/**
 * 会话状态
 */
sealed class SessionState {
    object IDLE : SessionState()
    object PREPARING : SessionState()
    object PREPARED : SessionState()
    object STREAMING : SessionState()
    object STOPPING : SessionState()
    data class ERROR(val error: StreamError) : SessionState()

    fun canTransitionTo(newState: SessionState): Boolean {
        return when (this) {
            IDLE -> newState is PREPARING
            PREPARING -> newState is PREPARED || newState is ERROR
            PREPARED -> newState is STREAMING || newState is ERROR
            STREAMING -> newState is STOPPING || newState is ERROR
            STOPPING -> newState is IDLE || newState is ERROR
            is ERROR -> newState is IDLE || newState is PREPARING
        }
    }
}

/**
 * 传输状态
 */
sealed class TransportState {
    object DISCONNECTED : TransportState()
    object CONNECTING : TransportState()
    object CONNECTED : TransportState()
    object STREAMING : TransportState()
    data class ERROR(val error: TransportError) : TransportState()
    object RECONNECTING : TransportState()
}

/**
 * 连接质量
 */
enum class ConnectionQuality {
    EXCELLENT,  // 延迟 < 50ms, 丢包率 < 0.1%
    GOOD,       // 延迟 < 150ms, 丢包率 < 1%
    FAIR,       // 延迟 < 300ms, 丢包率 < 3%
    POOR        // 延迟 > 300ms 或 丢包率 > 3%
}

/**
 * 推流统计信息
 */
data class StreamStats(
    val sessionDuration: Duration,
    val totalBytesSent: Long,
    val averageBitrate: Int,
    val currentFps: Int,
    val framesSent: Long,
    val framesDropped: Long,
    val activeTransports: Int,
    val connectionQuality: ConnectionQuality,
    val cpuUsage: Double,
    val memoryUsage: Long
)

/**
 * 传输统计信息
 */
data class TransportStats(
    val transportId: TransportId,
    val protocol: TransportProtocol,
    val state: TransportState,
    val bytesSent: Long,
    val packetsLost: Long,
    val rtt: Duration,
    val jitter: Duration,
    val bandwidth: Int,
    val connectionTime: Duration
)

/**
 * Surface提供者接口
 */
interface SurfaceProvider {
    fun getPreviewSurface(): Surface?
}

/**
 * 推流事件监听器
 */
interface StreamEventListener {
    fun onSessionStateChanged(state: SessionState) {}
    fun onTransportStateChanged(transportId: TransportId, state: TransportState) {}
    fun onConnectionQualityChanged(quality: ConnectionQuality) {}
    fun onStatsUpdated(stats: StreamStats) {}
    fun onError(error: StreamError) {}
}