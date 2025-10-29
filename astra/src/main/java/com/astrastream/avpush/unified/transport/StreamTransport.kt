package com.astrastream.avpush.unified.transport

import com.astrastream.avpush.unified.ConnectionQuality
import com.astrastream.avpush.unified.TransportState
import com.astrastream.avpush.unified.TransportStats
import com.astrastream.avpush.unified.config.TransportConfig
import com.astrastream.avpush.unified.config.TransportId
import com.astrastream.avpush.unified.config.TransportProtocol
import kotlinx.coroutines.flow.StateFlow

/**
 * 流数据基类
 */
sealed class StreamData {
    abstract val bytes: ByteArray
    abstract val timestamp: Long
}

/**
 * 音频数据
 */
data class AudioData(
    override val bytes: ByteArray,
    override val timestamp: Long,
    val sampleRate: Int,
    val channels: Int
) : StreamData() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AudioData

        if (!bytes.contentEquals(other.bytes)) return false
        if (timestamp != other.timestamp) return false
        if (sampleRate != other.sampleRate) return false
        if (channels != other.channels) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + sampleRate
        result = 31 * result + channels
        return result
    }
}

/**
 * 视频数据
 */
data class VideoData(
    override val bytes: ByteArray,
    override val timestamp: Long,
    val isKeyFrame: Boolean,
    val width: Int,
    val height: Int,
    val frameRate: Int
) : StreamData() {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as VideoData

        if (!bytes.contentEquals(other.bytes)) return false
        if (timestamp != other.timestamp) return false
        if (isKeyFrame != other.isKeyFrame) return false
        if (width != other.width) return false
        if (height != other.height) return false
        if (frameRate != other.frameRate) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + isKeyFrame.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + frameRate
        return result
    }
}

/**
 * 流传输接口
 */
interface StreamTransport {
    /**
     * 传输ID
     */
    val id: TransportId

    /**
     * 传输协议
     */
    val protocol: TransportProtocol

    /**
     * 传输状态
     */
    val state: StateFlow<TransportState>

    /**
     * 传输统计信息
     */
    val stats: StateFlow<TransportStats>

    /**
     * 连接到服务器
     */
    suspend fun connect()

    /**
     * 断开连接
     */
    suspend fun disconnect()

    /**
     * 发送音频数据
     *
     * @param data 音频数据
     */
    suspend fun sendAudioData(data: AudioData)

    /**
     * 发送视频数据
     *
     * @param data 视频数据
     */
    suspend fun sendVideoData(data: VideoData)

    /**
     * 更新码率
     *
     * @param bitrate 新的码率
     */
    fun updateBitrate(bitrate: Int)

    /**
     * 获取连接质量
     *
     * @return 连接质量
     */
    fun getConnectionQuality(): ConnectionQuality

    /**
     * 获取协议特定的统计信息
     *
     * @return 协议特定统计信息
     */
    fun getProtocolSpecificStats(): Map<String, Any>

    /**
     * 检查是否支持指定的功能
     *
     * @param capability 功能名称
     * @return 是否支持
     */
    fun supportsCapability(capability: String): Boolean

    /**
     * 获取支持的功能列表
     *
     * @return 支持的功能列表
     */
    fun getSupportedCapabilities(): List<String>
}

/**
 * 传输工厂接口
 */
interface TransportFactory {
    /**
     * 支持的协议
     */
    val supportedProtocol: TransportProtocol

    /**
     * 创建传输实例
     *
     * @param config 传输配置
     * @return 传输实例
     */
    fun create(config: TransportConfig): StreamTransport

    /**
     * 验证配置
     *
     * @param config 传输配置
     * @return 验证结果
     */
    fun validateConfig(config: TransportConfig): ConfigValidationResult
}

/**
 * 配置验证结果
 */
data class ConfigValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
)

/**
 * 传输注册表
 */
object TransportRegistry {
    private val factories = mutableMapOf<TransportProtocol, TransportFactory>()

    /**
     * 注册传输工厂
     *
     * @param factory 传输工厂
     */
    fun registerFactory(factory: TransportFactory) {
        factories[factory.supportedProtocol] = factory
    }

    /**
     * 创建传输实例
     *
     * @param config 传输配置
     * @return 传输实例
     */
    fun createTransport(config: TransportConfig): StreamTransport {
        val factory = factories[config.protocol]
            ?: throw UnsupportedOperationException("Protocol ${config.protocol} is not supported")

        val validationResult = factory.validateConfig(config)
        if (!validationResult.isValid) {
            throw IllegalArgumentException("Invalid config: ${validationResult.errors.joinToString()}")
        }

        return factory.create(config)
    }

    /**
     * 获取支持的协议列表
     *
     * @return 支持的协议列表
     */
    fun getSupportedProtocols(): List<TransportProtocol> {
        return factories.keys.toList()
    }

    /**
     * 检查是否支持指定协议
     *
     * @param protocol 协议
     * @return 是否支持
     */
    fun isProtocolSupported(protocol: TransportProtocol): Boolean {
        return factories.containsKey(protocol)
    }
}

/**
 * 传输能力常量
 */
object TransportCapabilities {
    const val LOW_LATENCY = "low_latency"
    const val HIGH_QUALITY = "high_quality"
    const val ADAPTIVE_BITRATE = "adaptive_bitrate"
    const val MULTIPLE_STREAMS = "multiple_streams"
    const val ENCRYPTION = "encryption"
    const val AUTHENTICATION = "authentication"
    const val RECONNECTION = "reconnection"
    const val STATISTICS = "statistics"
    const val QUALITY_MONITORING = "quality_monitoring"
}