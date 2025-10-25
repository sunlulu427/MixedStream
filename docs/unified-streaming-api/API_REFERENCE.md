# API参考文档

## 概述

本文档提供AVRtmpPushSDK统一推流接口的完整API参考，包括所有公开接口、类、方法和配置选项的详细说明。

## 核心接口

### UnifiedStreamSession

统一推流会话的主要接口，管理多协议推流的完整生命周期。

```kotlin
interface UnifiedStreamSession {
    val state: StateFlow<SessionState>
    val stats: StateFlow<StreamStats>

    suspend fun prepare(context: Context, surfaceProvider: SurfaceProvider)
    suspend fun start()
    suspend fun stop()
    suspend fun release()

    fun addTransport(config: TransportConfig): TransportId
    fun removeTransport(transportId: TransportId)
    fun switchPrimaryTransport(transportId: TransportId)

    fun updateVideoConfig(config: VideoConfig)
    fun updateAudioConfig(config: AudioConfig)

    fun observeTransportState(transportId: TransportId): Flow<TransportState>
    fun observeConnectionQuality(): Flow<ConnectionQuality>
    fun getAllTransportStats(): Flow<Map<TransportId, TransportStats>>
}
```

#### 方法详情

##### prepare(context: Context, surfaceProvider: SurfaceProvider)
准备推流会话，初始化音视频采集和编码器。

**参数:**
- `context`: Android上下文，用于访问系统资源
- `surfaceProvider`: 提供预览Surface的接口

**异常:**
- `StreamConfigurationException`: 配置参数无效
- `HardwareException`: 硬件初始化失败

##### start()
开始推流到所有已配置的传输。

**异常:**
- `TransportException`: 传输连接失败
- `EncodingException`: 编码器启动失败

##### stop()
停止推流但保持会话活跃，可以重新开始。

##### release()
释放所有资源并结束会话。

##### addTransport(config: TransportConfig): TransportId
添加新的传输协议。

**参数:**
- `config`: 传输配置，支持RTMP、WebRTC等

**返回值:**
- `TransportId`: 唯一的传输标识符

##### removeTransport(transportId: TransportId)
移除指定的传输。

**参数:**
- `transportId`: 要移除的传输ID

##### switchPrimaryTransport(transportId: TransportId)
切换主要传输协议。

**参数:**
- `transportId`: 新的主传输ID

##### updateVideoConfig(config: VideoConfig)
动态更新视频配置。

**参数:**
- `config`: 新的视频配置

##### updateAudioConfig(config: AudioConfig)
动态更新音频配置。

**参数:**
- `config`: 新的音频配置

## 配置类

### TransportConfig

传输配置的基类，使用密封类确保类型安全。

```kotlin
sealed class TransportConfig {
    abstract val id: TransportId
    abstract val priority: Int
    abstract val enabled: Boolean
}
```

### RtmpConfig

RTMP传输配置。

```kotlin
data class RtmpConfig(
    override val id: TransportId = TransportId.generate(),
    override val priority: Int = 1,
    override val enabled: Boolean = true,
    val pushUrl: String,
    val connectTimeout: Duration = 5.seconds,
    val retryPolicy: RetryPolicy = RetryPolicy.exponentialBackoff(),
    val chunkSize: Int = 4096,
    val enableLowLatency: Boolean = false
) : TransportConfig()
```

#### 参数说明

- `pushUrl`: RTMP推流地址，格式: `rtmp://server/app/stream`
- `connectTimeout`: 连接超时时间
- `retryPolicy`: 重试策略配置
- `chunkSize`: RTMP分块大小
- `enableLowLatency`: 是否启用低延迟模式

### WebRtcConfig

WebRTC传输配置。

```kotlin
data class WebRtcConfig(
    override val id: TransportId = TransportId.generate(),
    override val priority: Int = 0,
    override val enabled: Boolean = true,
    val signalingUrl: String,
    val roomId: String,
    val iceServers: List<IceServer> = emptyList(),
    val audioCodec: AudioCodec = AudioCodec.OPUS,
    val videoCodec: VideoCodec = VideoCodec.H264,
    val enableDataChannel: Boolean = false,
    val maxBitrate: Int = 2_000_000
) : TransportConfig()
```

#### 参数说明

- `signalingUrl`: 信令服务器地址
- `roomId`: 房间标识符
- `iceServers`: ICE服务器列表
- `audioCodec`: 音频编解码器
- `videoCodec`: 视频编解码器
- `enableDataChannel`: 是否启用数据通道
- `maxBitrate`: 最大码率限制

### VideoConfig

视频配置参数。

```kotlin
data class VideoConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val bitrate: Int = 2_000_000,
    val keyFrameInterval: Int = 2,
    val codec: VideoCodec = VideoCodec.H264,
    val profile: VideoProfile = VideoProfile.BASELINE,
    val enableHardwareAcceleration: Boolean = true,
    val bitrateMode: BitrateMode = BitrateMode.VBR,
    val enableAdaptiveBitrate: Boolean = true
)
```

#### 参数说明

- `width`: 视频宽度（像素）
- `height`: 视频高度（像素）
- `frameRate`: 帧率（fps）
- `bitrate`: 目标码率（bps）
- `keyFrameInterval`: 关键帧间隔（秒）
- `codec`: 视频编解码器
- `profile`: 编码配置文件
- `enableHardwareAcceleration`: 是否启用硬件加速
- `bitrateMode`: 码率控制模式
- `enableAdaptiveBitrate`: 是否启用自适应码率

### AudioConfig

音频配置参数。

```kotlin
data class AudioConfig(
    val sampleRate: Int = 44100,
    val bitrate: Int = 128_000,
    val channels: Int = 2,
    val codec: AudioCodec = AudioCodec.AAC,
    val enableAEC: Boolean = true,
    val enableAGC: Boolean = true,
    val enableNoiseReduction: Boolean = true,
    val bufferSize: Int = 4096
)
```

#### 参数说明

- `sampleRate`: 采样率（Hz）
- `bitrate`: 音频码率（bps）
- `channels`: 声道数
- `codec`: 音频编解码器
- `enableAEC`: 是否启用回声消除
- `enableAGC`: 是否启用自动增益控制
- `enableNoiseReduction`: 是否启用噪声抑制
- `bufferSize`: 音频缓冲区大小

### CameraConfig

摄像头配置参数。

```kotlin
data class CameraConfig(
    val facing: CameraFacing = CameraFacing.BACK,
    val autoFocus: Boolean = true,
    val enableFlash: Boolean = false,
    val stabilization: Boolean = true,
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val focusMode: FocusMode = FocusMode.CONTINUOUS_VIDEO
)
```

## 状态和枚举

### SessionState

会话状态枚举。

```kotlin
sealed class SessionState {
    object IDLE : SessionState()
    object PREPARING : SessionState()
    object PREPARED : SessionState()
    object STREAMING : SessionState()
    object STOPPING : SessionState()
    data class ERROR(val error: StreamError) : SessionState()
}
```

### TransportState

传输状态枚举。

```kotlin
sealed class TransportState {
    object DISCONNECTED : TransportState()
    object CONNECTING : TransportState()
    object CONNECTED : TransportState()
    object STREAMING : TransportState()
    data class ERROR(val error: TransportError) : TransportState()
    object RECONNECTING : TransportState()
}
```

### ConnectionQuality

连接质量枚举。

```kotlin
enum class ConnectionQuality {
    EXCELLENT,  // 延迟 < 50ms, 丢包率 < 0.1%
    GOOD,       // 延迟 < 150ms, 丢包率 < 1%
    FAIR,       // 延迟 < 300ms, 丢包率 < 3%
    POOR        // 延迟 > 300ms 或 丢包率 > 3%
}
```

### TransportProtocol

支持的传输协议。

```kotlin
enum class TransportProtocol(val displayName: String, val latency: Int) {
    RTMP("RTMP", 3000),
    WEBRTC("WebRTC", 100),
    SRT("SRT", 500),  // 规划中
    RTSP("RTSP", 1000) // 规划中
}
```

## 统计信息

### StreamStats

推流统计信息。

```kotlin
data class StreamStats(
    val sessionDuration: Duration,
    val totalBytesSent: Long,
    val averageBitrate: Int,
    val framesSent: Long,
    val framesDropped: Long,
    val activeTransports: Int,
    val connectionQuality: ConnectionQuality
)
```

### TransportStats

单个传输的统计信息。

```kotlin
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
```

## 错误处理

### StreamError

推流错误的基类。

```kotlin
sealed class StreamError {
    abstract val code: String
    abstract val message: String
    abstract val recoverable: Boolean
    abstract val timestamp: Instant
}
```

### TransportError

传输相关错误。

```kotlin
sealed class TransportError : StreamError() {
    data class ConnectionFailed(
        override val code: String = "TRANSPORT_CONNECTION_FAILED",
        override val message: String,
        val transport: TransportProtocol,
        override val timestamp: Instant = Clock.System.now()
    ) : TransportError() {
        override val recoverable: Boolean = true
    }

    data class AuthenticationFailed(
        override val code: String = "TRANSPORT_AUTH_FAILED",
        override val message: String,
        override val timestamp: Instant = Clock.System.now()
    ) : TransportError() {
        override val recoverable: Boolean = false
    }

    data class NetworkError(
        override val code: String = "NETWORK_ERROR",
        override val message: String,
        val errorCode: Int,
        override val timestamp: Instant = Clock.System.now()
    ) : TransportError() {
        override val recoverable: Boolean = true
    }
}
```

### EncodingError

编码相关错误。

```kotlin
sealed class EncodingError : StreamError() {
    data class HardwareEncoderFailed(
        override val code: String = "HARDWARE_ENCODER_FAILED",
        override val message: String,
        override val timestamp: Instant = Clock.System.now()
    ) : EncodingError() {
        override val recoverable: Boolean = true
    }

    data class ConfigurationError(
        override val code: String = "ENCODING_CONFIG_ERROR",
        override val message: String,
        val parameter: String,
        override val timestamp: Instant = Clock.System.now()
    ) : EncodingError() {
        override val recoverable: Boolean = false
    }
}
```

## 回调接口

### StreamEventListener

推流事件监听器。

```kotlin
interface StreamEventListener {
    fun onSessionStateChanged(state: SessionState) {}
    fun onTransportStateChanged(transportId: TransportId, state: TransportState) {}
    fun onConnectionQualityChanged(quality: ConnectionQuality) {}
    fun onStatsUpdated(stats: StreamStats) {}
    fun onError(error: StreamError) {}
}
```

### TransportEventListener

传输事件监听器。

```kotlin
interface TransportEventListener {
    fun onConnected(transportId: TransportId) {}
    fun onDisconnected(transportId: TransportId, reason: String) {}
    fun onDataSent(transportId: TransportId, bytes: Long) {}
    fun onError(transportId: TransportId, error: TransportError) {}
}
```

## 工厂方法和构建器

### createStreamSession

创建统一推流会话的工厂方法。

```kotlin
fun createStreamSession(
    block: StreamSessionBuilder.() -> Unit
): UnifiedStreamSession {
    return StreamSessionBuilder().apply(block).build()
}
```

### StreamSessionBuilder

推流会话构建器，使用Kotlin DSL语法。

```kotlin
class StreamSessionBuilder {
    private var videoConfig: VideoConfig? = null
    private var audioConfig: AudioConfig? = null
    private var cameraConfig: CameraConfig? = null
    private val transportConfigs = mutableListOf<TransportConfig>()
    private var advancedConfig: AdvancedConfig? = null

    fun video(block: VideoConfigBuilder.() -> Unit) {
        videoConfig = VideoConfigBuilder().apply(block).build()
    }

    fun audio(block: AudioConfigBuilder.() -> Unit) {
        audioConfig = AudioConfigBuilder().apply(block).build()
    }

    fun camera(block: CameraConfigBuilder.() -> Unit) {
        cameraConfig = CameraConfigBuilder().apply(block).build()
    }

    fun addRtmp(
        pushUrl: String,
        block: (RtmpConfigBuilder.() -> Unit)? = null
    ) {
        val builder = RtmpConfigBuilder().apply { this.pushUrl = pushUrl }
        block?.invoke(builder)
        transportConfigs.add(builder.build())
    }

    fun addWebRtc(
        signalingUrl: String,
        roomId: String,
        block: (WebRtcConfigBuilder.() -> Unit)? = null
    ) {
        val builder = WebRtcConfigBuilder().apply {
            this.signalingUrl = signalingUrl
            this.roomId = roomId
        }
        block?.invoke(builder)
        transportConfigs.add(builder.build())
    }

    fun advanced(block: AdvancedConfigBuilder.() -> Unit) {
        advancedConfig = AdvancedConfigBuilder().apply(block).build()
    }

    internal fun build(): UnifiedStreamSession {
        return UnifiedStreamSessionImpl(
            videoConfig = videoConfig ?: VideoConfig(),
            audioConfig = audioConfig ?: AudioConfig(),
            cameraConfig = cameraConfig ?: CameraConfig(),
            transportConfigs = transportConfigs,
            advancedConfig = advancedConfig ?: AdvancedConfig()
        )
    }
}
```

## 高级配置

### AdvancedConfig

高级配置选项。

```kotlin
data class AdvancedConfig(
    val enableSimultaneousPush: Boolean = false,
    val primaryTransport: TransportProtocol? = null,
    val fallbackEnabled: Boolean = true,
    val fallbackTransports: List<TransportProtocol> = emptyList(),
    val autoSwitchThreshold: ConnectionQuality = ConnectionQuality.POOR,
    val enableMetrics: Boolean = true,
    val metricsInterval: Duration = 1.seconds,
    val bufferStrategy: BufferStrategy = BufferStrategy.ADAPTIVE,
    val enableGpuAcceleration: Boolean = true
)
```

### RetryPolicy

重试策略配置。

```kotlin
data class RetryPolicy(
    val maxRetries: Int = 3,
    val baseDelay: Duration = 1.seconds,
    val maxDelay: Duration = 30.seconds,
    val backoffMultiplier: Double = 2.0,
    val jitter: Boolean = true
) {
    companion object {
        fun exponentialBackoff(
            maxRetries: Int = 3,
            baseDelay: Duration = 1.seconds,
            maxDelay: Duration = 30.seconds
        ): RetryPolicy = RetryPolicy(
            maxRetries = maxRetries,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            backoffMultiplier = 2.0,
            jitter = true
        )

        fun fixedDelay(
            maxRetries: Int = 3,
            delay: Duration = 5.seconds
        ): RetryPolicy = RetryPolicy(
            maxRetries = maxRetries,
            baseDelay = delay,
            maxDelay = delay,
            backoffMultiplier = 1.0,
            jitter = false
        )
    }

    fun getDelay(attempt: Int): Duration {
        val delay = minOf(
            baseDelay * backoffMultiplier.pow(attempt),
            maxDelay.inWholeMilliseconds.toDouble()
        ).toLong()

        return if (jitter) {
            Duration.ofMillis((delay * (0.5 + Random.nextDouble() * 0.5)).toLong())
        } else {
            Duration.ofMillis(delay)
        }
    }
}
```

## 扩展功能

### 插件接口

```kotlin
interface StreamPlugin {
    val name: String
    val version: String

    fun initialize(context: PluginContext)
    fun onFrameProcessed(frame: Frame): Frame
    fun onStateChanged(state: SessionState)
    fun cleanup()
}
```

### 自定义传输

```kotlin
abstract class CustomTransport : StreamTransport {
    abstract val protocolName: String
    abstract suspend fun establishConnection()
    abstract suspend fun sendData(data: ByteArray)
    abstract fun getProtocolSpecificStats(): Map<String, Any>
}
```

## 使用示例

### 基础推流

```kotlin
val session = createStreamSession {
    video {
        width = 1280
        height = 720
        frameRate = 30
        bitrate = 2_000_000
    }

    audio {
        sampleRate = 44100
        bitrate = 128_000
        enableAEC = true
    }

    camera {
        facing = CameraFacing.BACK
        autoFocus = true
    }

    addRtmp("rtmp://live.example.com/live/stream")
}

// 启动推流
lifecycleScope.launch {
    session.prepare(this@Activity, surfaceProvider)
    session.start()
}
```

### 多协议推流

```kotlin
val session = createStreamSession {
    // ... 基础配置

    addRtmp("rtmp://live.example.com/live/stream") {
        connectTimeout = 10.seconds
        enableLowLatency = true
    }

    addWebRtc("wss://signal.example.com", "room123") {
        audioCodec = AudioCodec.OPUS
        videoCodec = VideoCodec.H264
        maxBitrate = 3_000_000
    }

    advanced {
        enableSimultaneousPush = true
        primaryTransport = TransportProtocol.WEBRTC
        fallbackEnabled = true
    }
}
```

### 状态监听

```kotlin
// 监听会话状态
session.state.collect { state ->
    when (state) {
        SessionState.STREAMING -> showStreamingIndicator()
        SessionState.ERROR -> showError(state.error)
        else -> hideStreamingIndicator()
    }
}

// 监听连接质量
session.observeConnectionQuality().collect { quality ->
    updateQualityIndicator(quality)

    if (quality == ConnectionQuality.POOR) {
        // 可以选择降低码率或切换协议
        session.updateVideoConfig(
            currentVideoConfig.copy(bitrate = currentVideoConfig.bitrate / 2)
        )
    }
}
```

这份API参考文档为开发者提供了完整的接口说明和使用指导。