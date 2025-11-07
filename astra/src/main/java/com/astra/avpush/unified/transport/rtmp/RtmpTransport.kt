package com.astra.avpush.unified.transport.rtmp

import com.astra.avpush.infrastructure.stream.sender.rtmp.RtmpStreamSession
import com.astra.avpush.unified.ConnectionQuality
import com.astra.avpush.unified.TransportState
import com.astra.avpush.unified.TransportStats
import com.astra.avpush.unified.config.RtmpConfig
import com.astra.avpush.unified.config.TransportConfig
import com.astra.avpush.unified.config.TransportId
import com.astra.avpush.unified.config.TransportProtocol
import com.astra.avpush.unified.error.TransportError
import com.astra.avpush.unified.transport.AudioData
import com.astra.avpush.unified.transport.ConfigValidationResult
import com.astra.avpush.unified.transport.StreamTransport
import com.astra.avpush.unified.transport.TransportCapabilities
import com.astra.avpush.unified.transport.TransportFactory
import com.astra.avpush.unified.transport.VideoData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * RTMP传输实现
 *
 * 适配现有的RtmpStreamSession到新的统一传输接口
 */
class RtmpTransport(
    private val config: RtmpConfig
) : StreamTransport {

    override val id: TransportId = config.id
    override val protocol: TransportProtocol = TransportProtocol.RTMP

    private val _state = MutableStateFlow<TransportState>(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(createInitialStats())
    override val stats: StateFlow<TransportStats> = _stats.asStateFlow()

    // 使用现有的RtmpStreamSession实现
    private var streamSession: RtmpStreamSession? = null

    // 统计信息
    private val bytesSent = AtomicLong(0)
    private val packetsLost = AtomicLong(0)
    private val connectionStartTime = AtomicLong(0)
    private var lastStatsUpdate = Instant.now()

    // 协程作用域
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // 重试机制
    private var retryCount = 0
    private var isReconnecting = false

    override suspend fun connect() {
        if (_state.value != TransportState.DISCONNECTED) {
            return
        }

        _state.value = TransportState.CONNECTING
        connectionStartTime.set(System.currentTimeMillis())

        try {
            // 创建RtmpStreamSession实例
            streamSession = RtmpStreamSession().apply {
                // 配置RTMP参数
                configureRtmpSession(this)
            }

            // 配置RTMP服务器地址并连接
            streamSession?.setDataSource(config.pushUrl)
            streamSession?.connect()

            _state.value = TransportState.CONNECTED
            retryCount = 0
            isReconnecting = false

            // 启动统计信息更新
            startStatsCollection()

        } catch (e: Exception) {
            _state.value = TransportState.ERROR(
                TransportError.ConnectionFailed(
                    message = "RTMP connection failed: ${e.message}",
                    transport = protocol
                )
            )

            // 尝试重连
            if (config.retryPolicy.maxRetries > 0 && retryCount < config.retryPolicy.maxRetries) {
                scheduleReconnect()
            }
        }
    }

    override suspend fun disconnect() {
        scope.cancel()

        try {
            streamSession?.close()
        } catch (e: Exception) {
            // 忽略断开连接时的错误
        }

        streamSession = null
        _state.value = TransportState.DISCONNECTED

        // 重置统计信息
        bytesSent.set(0)
        packetsLost.set(0)
        connectionStartTime.set(0)
    }

    override suspend fun sendAudioData(data: AudioData) {
        ensureConnected()

        try {
            // Convert AudioData to the format expected by RtmpStreamSession
            // For now, we'll need to create a ByteBuffer and MediaCodec.BufferInfo
            // This is a placeholder implementation that needs proper integration
            // streamSession?.pushAudio(audioBuffer, bufferInfo)
            bytesSent.addAndGet(data.bytes.size.toLong())

            if (_state.value != TransportState.STREAMING) {
                _state.value = TransportState.STREAMING
            }
        } catch (e: Exception) {
            handleSendError(e)
        }
    }

    override suspend fun sendVideoData(data: VideoData) {
        ensureConnected()

        try {
            // Convert VideoData to the format expected by RtmpStreamSession
            // For now, we'll need to create a ByteBuffer and MediaCodec.BufferInfo
            // This is a placeholder implementation that needs proper integration
            // streamSession?.pushVideo(videoBuffer, bufferInfo)
            bytesSent.addAndGet(data.bytes.size.toLong())

            if (_state.value != TransportState.STREAMING) {
                _state.value = TransportState.STREAMING
            }
        } catch (e: Exception) {
            handleSendError(e)
        }
    }

    override fun updateBitrate(bitrate: Int) {
        // RTMP传输的码率更新通常在编码器层面处理
        // 这里可以记录目标码率用于统计
    }

    override fun getConnectionQuality(): ConnectionQuality {
        return when (_state.value) {
            TransportState.STREAMING -> {
                val currentStats = _stats.value
                val rttMs = currentStats.rtt.toMillis()
                val lossRate = if (bytesSent.get() > 0) {
                    packetsLost.get().toDouble() / bytesSent.get() * 100
                } else 0.0

                when {
                    rttMs < 50 && lossRate < 0.1 -> ConnectionQuality.EXCELLENT
                    rttMs < 150 && lossRate < 1.0 -> ConnectionQuality.GOOD
                    rttMs < 300 && lossRate < 3.0 -> ConnectionQuality.FAIR
                    else -> ConnectionQuality.POOR
                }
            }
            TransportState.CONNECTED -> ConnectionQuality.GOOD
            TransportState.CONNECTING, TransportState.RECONNECTING -> ConnectionQuality.FAIR
            else -> ConnectionQuality.POOR
        }
    }

    override fun getProtocolSpecificStats(): Map<String, Any> {
        return mapOf(
            "rtmp_version" to "1.0",
            "chunk_size" to config.chunkSize,
            "low_latency_enabled" to config.enableLowLatency,
            "tcp_no_delay" to config.enableTcpNoDelay,
            "connection_url" to config.pushUrl,
            "retry_count" to retryCount,
            "is_reconnecting" to isReconnecting
        )
    }

    override fun supportsCapability(capability: String): Boolean {
        return when (capability) {
            TransportCapabilities.ADAPTIVE_BITRATE -> true
            TransportCapabilities.RECONNECTION -> true
            TransportCapabilities.STATISTICS -> true
            TransportCapabilities.QUALITY_MONITORING -> true
            TransportCapabilities.LOW_LATENCY -> config.enableLowLatency
            TransportCapabilities.ENCRYPTION -> false // RTMP不支持内置加密
            TransportCapabilities.AUTHENTICATION -> true
            else -> false
        }
    }

    override fun getSupportedCapabilities(): List<String> {
        return listOf(
            TransportCapabilities.ADAPTIVE_BITRATE,
            TransportCapabilities.RECONNECTION,
            TransportCapabilities.STATISTICS,
            TransportCapabilities.QUALITY_MONITORING,
            TransportCapabilities.AUTHENTICATION
        ).plus(
            if (config.enableLowLatency) listOf(TransportCapabilities.LOW_LATENCY) else emptyList()
        )
    }

    private fun configureRtmpSession(session: RtmpStreamSession) {
        // 配置RTMP发送器的参数
        // 这里可以根据config设置各种RTMP参数
    }

    private fun ensureConnected() {
        if (_state.value !in listOf(TransportState.CONNECTED, TransportState.STREAMING)) {
            throw IllegalStateException("Transport is not connected")
        }
    }

    private fun handleSendError(error: Exception) {
        packetsLost.incrementAndGet()

        _state.value = TransportState.ERROR(
            TransportError.NetworkError(
                message = "Failed to send data: ${error.message}",
                errorCode = -1,
                transport = protocol
            )
        )

        // 尝试重连
        if (!isReconnecting && config.retryPolicy.maxRetries > 0) {
            scope.launch { scheduleReconnect() }
        }
    }

    private suspend fun scheduleReconnect() {
        if (isReconnecting) return

        isReconnecting = true
        _state.value = TransportState.RECONNECTING

        val delay = config.retryPolicy.getDelay(retryCount)
        delay(delay.toMillis())

        retryCount++

        try {
            disconnect()
            connect()
        } catch (e: Exception) {
            if (retryCount >= config.retryPolicy.maxRetries) {
                _state.value = TransportState.ERROR(
                    TransportError.ConnectionFailed(
                        message = "Max retry attempts exceeded: ${e.message}",
                        transport = protocol
                    )
                )
                isReconnecting = false
            } else {
                scheduleReconnect()
            }
        }
    }

    private fun startStatsCollection() {
        scope.launch {
            while (isActive && _state.value != TransportState.DISCONNECTED) {
                updateStats()
                delay(1000) // 每秒更新一次统计信息
            }
        }
    }

    private fun updateStats() {
        val now = Instant.now()
        val connectionTime = if (connectionStartTime.get() > 0) {
            Duration.ofMillis(System.currentTimeMillis() - connectionStartTime.get())
        } else {
            Duration.ZERO
        }

        _stats.value = TransportStats(
            transportId = id,
            protocol = protocol,
            state = _state.value,
            bytesSent = bytesSent.get(),
            packetsLost = packetsLost.get(),
            rtt = estimateRTT(),
            jitter = Duration.ofMillis(10), // RTMP的抖动通常较低
            bandwidth = calculateBandwidth(),
            connectionTime = connectionTime
        )

        lastStatsUpdate = now
    }

    private fun estimateRTT(): Duration {
        // 简单的RTT估算，实际实现可能需要更复杂的逻辑
        return when (getConnectionQuality()) {
            ConnectionQuality.EXCELLENT -> Duration.ofMillis(30)
            ConnectionQuality.GOOD -> Duration.ofMillis(100)
            ConnectionQuality.FAIR -> Duration.ofMillis(250)
            ConnectionQuality.POOR -> Duration.ofMillis(500)
        }
    }

    private fun calculateBandwidth(): Int {
        val timeDiff = Duration.between(lastStatsUpdate, Instant.now()).toMillis()
        return if (timeDiff > 0) {
            ((bytesSent.get() * 8 * 1000) / timeDiff).toInt() // bps
        } else {
            0
        }
    }

    private fun createInitialStats(): TransportStats {
        return TransportStats(
            transportId = id,
            protocol = protocol,
            state = TransportState.DISCONNECTED,
            bytesSent = 0,
            packetsLost = 0,
            rtt = Duration.ZERO,
            jitter = Duration.ZERO,
            bandwidth = 0,
            connectionTime = Duration.ZERO
        )
    }
}

/**
 * RTMP传输工厂
 */
class RtmpTransportFactory : TransportFactory {
    override val supportedProtocol: TransportProtocol = TransportProtocol.RTMP

    override fun create(config: TransportConfig): StreamTransport {
        require(config is RtmpConfig) { "Expected RtmpConfig, got ${config::class.simpleName}" }
        return RtmpTransport(config)
    }

    override fun validateConfig(config: TransportConfig): ConfigValidationResult {
        if (config !is RtmpConfig) {
            return ConfigValidationResult(
                isValid = false,
                errors = listOf("Expected RtmpConfig, got ${config::class.simpleName}")
            )
        }

        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // 验证推流URL
        if (config.pushUrl.isBlank()) {
            errors.add("Push URL cannot be empty")
        } else if (!config.pushUrl.startsWith("rtmp://") && !config.pushUrl.startsWith("rtmps://")) {
            errors.add("Push URL must start with rtmp:// or rtmps://")
        }

        // 验证连接超时
        if (config.connectTimeout.isNegative || config.connectTimeout.isZero) {
            errors.add("Connect timeout must be positive")
        }

        // 验证分块大小
        if (config.chunkSize <= 0) {
            errors.add("Chunk size must be positive")
        } else if (config.chunkSize < 128) {
            warnings.add("Very small chunk size may affect performance")
        } else if (config.chunkSize > 65536) {
            warnings.add("Very large chunk size may cause compatibility issues")
        }

        // 验证重试策略
        if (config.retryPolicy.maxRetries < 0) {
            errors.add("Max retries cannot be negative")
        }

        return ConfigValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings
        )
    }
}
