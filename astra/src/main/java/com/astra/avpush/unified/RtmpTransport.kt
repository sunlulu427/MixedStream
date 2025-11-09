package com.astra.avpush.unified

import com.astra.avpush.infrastructure.stream.nativebridge.NativeSender
import com.astra.avpush.infrastructure.stream.nativebridge.NativeSenderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Duration

/**
 * 轻量级 RTMP 传输实现，直接复用 NativeSenderBridge。
 */
class RtmpTransport(private val config: RtmpConfig) : StreamTransport {

    override val id: TransportId = config.id
    override val protocol: TransportProtocol = TransportProtocol.RTMP

    private val sender: NativeSender = NativeSenderFactory.createForProtocol(protocol)

    private val _state = MutableStateFlow<TransportState>(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(
        TransportStats(
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
    )
    override val stats: StateFlow<TransportStats> = _stats.asStateFlow()

    override suspend fun connect() {
        if (_state.value == TransportState.STREAMING || _state.value == TransportState.CONNECTING) {
            return
        }
        _state.value = TransportState.CONNECTING
        runCatching { withContext(Dispatchers.IO) { sender.connect(config.pushUrl) } }
            .onSuccess {
                _state.value = TransportState.STREAMING
            }
            .onFailure { error ->
                _state.value = TransportState.ERROR(
                    TransportError.ConnectionFailed(
                        transport = protocol,
                        detail = error.message ?: "RTMP connection failed",
                        error = error
                    )
                )
            }
    }

    override suspend fun disconnect() {
        sender.close()
        _state.value = TransportState.DISCONNECTED
    }

    override suspend fun sendAudioData(data: AudioData) {
        // Native pipeline already owns audio capture/encode; no action required.
    }

    override suspend fun sendVideoData(data: VideoData) {
        // Native pipeline already owns video capture/encode; no action required.
    }

    override fun updateBitrate(bitrate: Int) {
        sender.updateVideoBps(bitrate)
    }

    override fun getConnectionQuality(): ConnectionQuality {
        return when (_state.value) {
            TransportState.STREAMING -> ConnectionQuality.GOOD
            TransportState.CONNECTING -> ConnectionQuality.FAIR
            is TransportState.ERROR -> ConnectionQuality.POOR
            else -> ConnectionQuality.FAIR
        }
    }

    override fun getProtocolSpecificStats(): Map<String, Any> = emptyMap()

    override fun supportsCapability(capability: String): Boolean {
        return capability in listOf(
            TransportCapabilities.RECONNECTION,
            TransportCapabilities.ADAPTIVE_BITRATE,
            TransportCapabilities.STATISTICS
        )
    }

    override fun getSupportedCapabilities(): List<String> = listOf(
        TransportCapabilities.RECONNECTION,
        TransportCapabilities.ADAPTIVE_BITRATE,
        TransportCapabilities.STATISTICS
    )
}

class RtmpTransportFactory : TransportFactory {
    override val supportedProtocol: TransportProtocol = TransportProtocol.RTMP

    override fun create(config: TransportConfig): StreamTransport {
        require(config is RtmpConfig) { "Expected RtmpConfig, got ${config::class.simpleName}" }
        return RtmpTransport(config)
    }

    override fun validateConfig(config: TransportConfig): ConfigValidationResult {
        if (config !is RtmpConfig) {
            return ConfigValidationResult(isValid = false, errors = listOf("Invalid config type"))
        }
        val errors = mutableListOf<String>()
        if (config.pushUrl.isBlank()) {
            errors += "Push URL cannot be empty"
        }
        if (!config.pushUrl.startsWith("rtmp://") && !config.pushUrl.startsWith("rtmps://")) {
            errors += "Push URL must start with rtmp:// or rtmps://"
        }
        return ConfigValidationResult(errors.isEmpty(), errors)
    }
}
