package com.astrastream.avpush.unified.builder

import com.astrastream.avpush.unified.UnifiedStreamSession
import com.astrastream.avpush.unified.ConnectionQuality
import com.astrastream.avpush.unified.config.*
import com.astrastream.avpush.unified.impl.UnifiedStreamSessionImpl
import java.time.Duration

/**
 * 创建统一推流会话的工厂方法
 */
fun createStreamSession(
    block: StreamSessionBuilder.() -> Unit
): UnifiedStreamSession {
    return StreamSessionBuilder().apply(block).build()
}

/**
 * 推流会话构建器，使用Kotlin DSL语法
 */
class StreamSessionBuilder {
    private var videoConfig: VideoConfig? = null
    private var audioConfig: AudioConfig? = null
    private var cameraConfig: CameraConfig? = null
    private val transportConfigs = mutableListOf<TransportConfig>()
    private var advancedConfig: AdvancedConfig? = null

    /**
     * 配置视频参数
     */
    fun video(block: VideoConfigBuilder.() -> Unit) {
        videoConfig = VideoConfigBuilder().apply(block).build()
    }

    /**
     * 配置音频参数
     */
    fun audio(block: AudioConfigBuilder.() -> Unit) {
        audioConfig = AudioConfigBuilder().apply(block).build()
    }

    /**
     * 配置摄像头参数
     */
    fun camera(block: CameraConfigBuilder.() -> Unit) {
        cameraConfig = CameraConfigBuilder().apply(block).build()
    }

    /**
     * 添加流传输（自动检测协议）
     * 根据URL自动选择RTMP、WebRTC或SRT传输
     */
    fun addStream(url: String): TransportId {
        val config = com.astrastream.avpush.unified.ProtocolDetector.detectAndCreateConfig(url)
        transportConfigs.add(config)
        return config.id
    }

    /**
     * 添加RTMP传输
     */
    fun addRtmp(
        pushUrl: String,
        block: (RtmpConfigBuilder.() -> Unit)? = null
    ): TransportId {
        val builder = RtmpConfigBuilder().apply { this.pushUrl = pushUrl }
        block?.invoke(builder)
        val config = builder.build()
        transportConfigs.add(config)
        return config.id
    }

    /**
     * 添加WebRTC传输
     */
    fun addWebRtc(
        signalingUrl: String,
        roomId: String,
        block: (WebRtcConfigBuilder.() -> Unit)? = null
    ): TransportId {
        val builder = WebRtcConfigBuilder().apply {
            this.signalingUrl = signalingUrl
            this.roomId = roomId
        }
        block?.invoke(builder)
        val config = builder.build()
        transportConfigs.add(config)
        return config.id
    }

    /**
     * 添加SRT传输
     */
    fun addSrt(
        serverUrl: String,
        block: (SrtConfigBuilder.() -> Unit)? = null
    ): TransportId {
        val builder = SrtConfigBuilder().apply { this.serverUrl = serverUrl }
        block?.invoke(builder)
        val config = builder.build()
        transportConfigs.add(config)
        return config.id
    }

    /**
     * 高级配置
     */
    fun advanced(block: AdvancedConfigBuilder.() -> Unit) {
        advancedConfig = AdvancedConfigBuilder().apply(block).build()
    }

    internal fun build(): UnifiedStreamSession {
        return UnifiedStreamSessionImpl(
            videoConfig = videoConfig ?: VideoConfig(),
            audioConfig = audioConfig ?: AudioConfig(),
            cameraConfig = cameraConfig ?: CameraConfig(),
            initialTransportConfigs = transportConfigs,
            advancedConfig = advancedConfig ?: AdvancedConfig()
        )
    }
}

/**
 * 视频配置构建器
 */
class VideoConfigBuilder {
    var width: Int = 1280
    var height: Int = 720
    var frameRate: Int = 30
    var bitrate: Int = 2_000_000
    var keyFrameInterval: Int = 2
    var codec: VideoCodec = VideoCodec.H264
    var profile: VideoProfile = VideoProfile.BASELINE
    var level: VideoLevel = VideoLevel.LEVEL_3_1
    var enableHardwareAcceleration: Boolean = true
    var bitrateMode: BitrateMode = BitrateMode.VBR
    var enableAdaptiveBitrate: Boolean = true
    var minBitrate: Int = 300_000
    var maxBitrate: Int = 4_000_000
    var qualityPreset: QualityPreset = QualityPreset.BALANCED
    var enableBFrames: Boolean = false
    var maxBFrames: Int = 0
    var enableLowLatency: Boolean = false
    var tuning: EncoderTuning = EncoderTuning.DEFAULT
    var preferredEncoder: String? = null

    fun build(): VideoConfig = VideoConfig(
        width = width,
        height = height,
        frameRate = frameRate,
        bitrate = bitrate,
        keyFrameInterval = keyFrameInterval,
        codec = codec,
        profile = profile,
        level = level,
        enableHardwareAcceleration = enableHardwareAcceleration,
        bitrateMode = bitrateMode,
        enableAdaptiveBitrate = enableAdaptiveBitrate,
        minBitrate = minBitrate,
        maxBitrate = maxBitrate,
        qualityPreset = qualityPreset,
        enableBFrames = enableBFrames,
        maxBFrames = maxBFrames,
        enableLowLatency = enableLowLatency,
        tuning = tuning,
        preferredEncoder = preferredEncoder
    )
}

/**
 * 音频配置构建器
 */
class AudioConfigBuilder {
    var sampleRate: Int = 44100
    var bitrate: Int = 128_000
    var channels: Int = 2
    var codec: AudioCodec = AudioCodec.AAC
    var profile: AudioProfile = AudioProfile.LC
    var enableAEC: Boolean = true
    var enableAGC: Boolean = true
    var enableNoiseReduction: Boolean = true
    var enableHighPassFilter: Boolean = false
    var enableVBR: Boolean = false
    var bufferSize: Int = 4096
    var enableLowLatency: Boolean = false
    var enableWindNoiseReduction: Boolean = false

    fun build(): AudioConfig = AudioConfig(
        sampleRate = sampleRate,
        bitrate = bitrate,
        channels = channels,
        codec = codec,
        profile = profile,
        enableAEC = enableAEC,
        enableAGC = enableAGC,
        enableNoiseReduction = enableNoiseReduction,
        enableHighPassFilter = enableHighPassFilter,
        enableVBR = enableVBR,
        bufferSize = bufferSize,
        enableLowLatency = enableLowLatency,
        enableWindNoiseReduction = enableWindNoiseReduction
    )
}

/**
 * 摄像头配置构建器
 */
class CameraConfigBuilder {
    var facing: CameraFacing = CameraFacing.BACK
    var autoFocus: Boolean = true
    var enableFlash: Boolean = false
    var stabilization: Boolean = true
    var exposureMode: ExposureMode = ExposureMode.AUTO
    var whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO
    var focusMode: FocusMode = FocusMode.CONTINUOUS_VIDEO
    var zoomLevel: Float = 1.0f
    var enableFaceDetection: Boolean = false

    fun build(): CameraConfig = CameraConfig(
        facing = facing,
        autoFocus = autoFocus,
        enableFlash = enableFlash,
        stabilization = stabilization,
        exposureMode = exposureMode,
        whiteBalanceMode = whiteBalanceMode,
        focusMode = focusMode,
        zoomLevel = zoomLevel,
        enableFaceDetection = enableFaceDetection
    )
}

/**
 * RTMP配置构建器
 */
class RtmpConfigBuilder {
    var id: TransportId = TransportConfig.generateId()
    var priority: Int = 1
    var enabled: Boolean = true
    var pushUrl: String = ""
    var connectTimeout: Duration = Duration.ofSeconds(10)
    var retryPolicy: RetryPolicy = RetryPolicy.exponentialBackoff()
    var chunkSize: Int = 4096
    var enableLowLatency: Boolean = false
    var enableTcpNoDelay: Boolean = true

    fun build(): RtmpConfig = RtmpConfig(
        id = id,
        priority = priority,
        enabled = enabled,
        pushUrl = pushUrl,
        connectTimeout = connectTimeout,
        retryPolicy = retryPolicy,
        chunkSize = chunkSize,
        enableLowLatency = enableLowLatency,
        enableTcpNoDelay = enableTcpNoDelay
    )
}

/**
 * WebRTC配置构建器
 */
class WebRtcConfigBuilder {
    var id: TransportId = TransportConfig.generateId()
    var priority: Int = 0
    var enabled: Boolean = true
    var signalingUrl: String = ""
    var roomId: String = ""
    var iceServers: List<IceServer> = emptyList()
    var audioCodec: AudioCodec = AudioCodec.OPUS
    var videoCodec: VideoCodec = VideoCodec.H264
    var enableDataChannel: Boolean = false
    var maxBitrate: Int = 2_000_000
    var enableDtls: Boolean = true

    fun build(): WebRtcConfig = WebRtcConfig(
        id = id,
        priority = priority,
        enabled = enabled,
        signalingUrl = signalingUrl,
        roomId = roomId,
        iceServers = iceServers,
        audioCodec = audioCodec,
        videoCodec = videoCodec,
        enableDataChannel = enableDataChannel,
        maxBitrate = maxBitrate,
        enableDtls = enableDtls
    )
}

/**
 * SRT配置构建器
 */
class SrtConfigBuilder {
    var id: TransportId = TransportConfig.generateId()
    var priority: Int = 2
    var enabled: Boolean = true
    var serverUrl: String = ""
    var latency: Duration = Duration.ofMillis(500)
    var encryption: SrtEncryption = SrtEncryption.NONE
    var streamId: String? = null

    fun build(): SrtConfig = SrtConfig(
        id = id,
        priority = priority,
        enabled = enabled,
        serverUrl = serverUrl,
        latency = latency,
        encryption = encryption,
        streamId = streamId
    )
}

/**
 * 高级配置
 */
data class AdvancedConfig(
    val enableSimultaneousPush: Boolean = false,
    val primaryTransport: TransportProtocol? = null,
    val fallbackEnabled: Boolean = true,
    val fallbackTransports: List<TransportProtocol> = emptyList(),
    val autoSwitchThreshold: ConnectionQuality = ConnectionQuality.POOR,
    val enableMetrics: Boolean = true,
    val metricsInterval: Duration = Duration.ofSeconds(1),
    val bufferStrategy: BufferStrategy = BufferStrategy.ADAPTIVE,
    val enableGpuAcceleration: Boolean = true,
    val enableMemoryPool: Boolean = true,
    val maxBufferSize: Long = 50 * 1024 * 1024, // 50MB
    val enableTcpNoDelay: Boolean = true,
    val socketBufferSize: Int = 64 * 1024,
    val enableParallelEncoding: Boolean = true,
    val encoderThreads: Int = 2,
    val enablePreprocessing: Boolean = true,
    val preprocessingThreads: Int = 1,
    val enableDebugMode: Boolean = false,
    val enableVerboseLogging: Boolean = false,
    val enablePerformanceMetrics: Boolean = true,
    val enableDetailedStats: Boolean = true,
    val statsInterval: Duration = Duration.ofSeconds(1),
    val enableNetworkDiagnostics: Boolean = true,
    val enableEncoderDiagnostics: Boolean = true,
    val enableFrameAnalysis: Boolean = false,
    val enableErrorReporting: Boolean = true,
    val errorReportingLevel: ErrorLevel = ErrorLevel.WARNING,
    val enableAggressiveRetry: Boolean = false
)

/**
 * 高级配置构建器
 */
class AdvancedConfigBuilder {
    var enableSimultaneousPush: Boolean = false
    var primaryTransport: TransportProtocol? = null
    var fallbackEnabled: Boolean = true
    var fallbackTransports: List<TransportProtocol> = emptyList()
    var autoSwitchThreshold: ConnectionQuality = ConnectionQuality.POOR
    var enableMetrics: Boolean = true
    var metricsInterval: Duration = Duration.ofSeconds(1)
    var bufferStrategy: BufferStrategy = BufferStrategy.ADAPTIVE
    var enableGpuAcceleration: Boolean = true
    var enableMemoryPool: Boolean = true
    var maxBufferSize: Long = 50 * 1024 * 1024
    var enableTcpNoDelay: Boolean = true
    var socketBufferSize: Int = 64 * 1024
    var enableParallelEncoding: Boolean = true
    var encoderThreads: Int = 2
    var enablePreprocessing: Boolean = true
    var preprocessingThreads: Int = 1
    var enableDebugMode: Boolean = false
    var enableVerboseLogging: Boolean = false
    var enablePerformanceMetrics: Boolean = true
    var enableDetailedStats: Boolean = true
    var statsInterval: Duration = Duration.ofSeconds(1)
    var enableNetworkDiagnostics: Boolean = true
    var enableEncoderDiagnostics: Boolean = true
    var enableFrameAnalysis: Boolean = false
    var enableErrorReporting: Boolean = true
    var errorReportingLevel: ErrorLevel = ErrorLevel.WARNING
    var enableAggressiveRetry: Boolean = false

    fun build(): AdvancedConfig = AdvancedConfig(
        enableSimultaneousPush = enableSimultaneousPush,
        primaryTransport = primaryTransport,
        fallbackEnabled = fallbackEnabled,
        fallbackTransports = fallbackTransports,
        autoSwitchThreshold = autoSwitchThreshold,
        enableMetrics = enableMetrics,
        metricsInterval = metricsInterval,
        bufferStrategy = bufferStrategy,
        enableGpuAcceleration = enableGpuAcceleration,
        enableMemoryPool = enableMemoryPool,
        maxBufferSize = maxBufferSize,
        enableTcpNoDelay = enableTcpNoDelay,
        socketBufferSize = socketBufferSize,
        enableParallelEncoding = enableParallelEncoding,
        encoderThreads = encoderThreads,
        enablePreprocessing = enablePreprocessing,
        preprocessingThreads = preprocessingThreads,
        enableDebugMode = enableDebugMode,
        enableVerboseLogging = enableVerboseLogging,
        enablePerformanceMetrics = enablePerformanceMetrics,
        enableDetailedStats = enableDetailedStats,
        statsInterval = statsInterval,
        enableNetworkDiagnostics = enableNetworkDiagnostics,
        enableEncoderDiagnostics = enableEncoderDiagnostics,
        enableFrameAnalysis = enableFrameAnalysis,
        enableErrorReporting = enableErrorReporting,
        errorReportingLevel = errorReportingLevel,
        enableAggressiveRetry = enableAggressiveRetry
    )
}

/**
 * 缓冲策略
 */
enum class BufferStrategy {
    FIXED,      // 固定大小缓冲
    ADAPTIVE,   // 自适应缓冲
    LOW_LATENCY // 低延迟缓冲
}

/**
 * 错误报告级别
 */
enum class ErrorLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
    CRITICAL
}