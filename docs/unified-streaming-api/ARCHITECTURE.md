# 架构设计文档

## 概述

本文档详细描述了AVRtmpPushSDK统一推流接口的系统架构设计，包括整体架构、模块划分、数据流向、设计模式和扩展机制。

## 整体架构

### 分层架构

统一推流SDK采用Clean Architecture的分层设计：

```
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  ┌─────────────────┐  ┌─────────────────┐  ┌──────────────┐ │
│  │   AVLiveView    │  │ StreamSession   │  │ Legacy APIs  │ │
│  │   (UI Widget)   │  │   Builder       │  │  (Adapter)   │ │
│  └─────────────────┘  └─────────────────┘  └──────────────┘ │
└─────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────┐
│                   Use Case Layer                           │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │            UnifiedStreamSession                         │ │
│  │  ┌─────────────┐ ┌─────────────┐ ┌─────────────────────┐ │ │
│  │  │   Session   │ │ Transport   │ │    Configuration    │ │ │
│  │  │ Controller  │ │  Manager    │ │     Manager         │ │ │
│  │  └─────────────┘ └─────────────┘ └─────────────────────┘ │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                               │
┌─────────────────────────────────────────────────────────────┐
│                Infrastructure Layer                        │
│  ┌─────────────┐ ┌─────────────┐ ┌─────────────┐ ┌─────────┐ │
│  │    RTMP     │ │   WebRTC    │ │     SRT     │ │   ...   │ │
│  │ Transport   │ │ Transport   │ │ Transport   │ │Transport│ │
│  └─────────────┘ └─────────────┘ └─────────────┘ └─────────┘ │
│  ┌─────────────────────────────────────────────────────────┐ │
│  │              Existing AV Pipeline                      │ │
│  │  Audio/Video Capture → Encode → Pipeline → Transport   │ │
│  └─────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 核心原则

1. **依赖倒置**: 高层模块不依赖低层模块，都依赖于抽象
2. **单一职责**: 每个模块只负责一个特定功能
3. **开闭原则**: 对扩展开放，对修改封闭
4. **接口隔离**: 客户端不应该依赖它不需要的接口

## 模块设计

### 1. Application Layer（应用层）

#### AVLiveView Widget
```kotlin
class AVLiveView : SurfaceView {
    // 现有UI组件，保持向后兼容
    private val sessionAdapter: StreamSessionAdapter

    fun setAudioConfigure(config: AudioConfigure) {
        sessionAdapter.updateAudioConfig(config.toNewFormat())
    }

    fun startLive() {
        sessionAdapter.start()
    }
}
```

#### StreamSession Builder
```kotlin
class StreamSessionBuilder {
    private val configBuilder = ConfigurationBuilder()
    private val transportBuilder = TransportBuilder()

    fun build(): UnifiedStreamSession {
        return UnifiedStreamSessionImpl(
            config = configBuilder.build(),
            transports = transportBuilder.build()
        )
    }
}
```

#### Legacy Adapter
```kotlin
class LegacyStreamAdapter : LiveStreamSession {
    private val unifiedSession: UnifiedStreamSession

    override fun startLive() {
        // 转换调用到新接口
        runBlocking { unifiedSession.start() }
    }
}
```

### 2. Use Case Layer（用例层）

#### UnifiedStreamSession
会话管理的核心接口，协调所有子系统：

```kotlin
interface UnifiedStreamSession {
    // 生命周期管理
    suspend fun prepare(context: Context, surfaceProvider: SurfaceProvider)
    suspend fun start()
    suspend fun stop()
    suspend fun release()

    // 传输管理
    fun addTransport(config: TransportConfig): TransportId
    fun removeTransport(transportId: TransportId)
    fun switchPrimaryTransport(transportId: TransportId)

    // 配置管理
    fun updateVideoConfig(config: VideoConfig)
    fun updateAudioConfig(config: AudioConfig)

    // 状态观察
    val state: StateFlow<SessionState>
    fun observeTransportState(transportId: TransportId): Flow<TransportState>
    fun observeConnectionQuality(): Flow<ConnectionQuality>
}
```

#### Session Controller
会话控制器，管理会话的整个生命周期：

```kotlin
class SessionController(
    private val configManager: ConfigurationManager,
    private val transportManager: TransportManager,
    private val pipelineManager: PipelineManager
) {
    private val _state = MutableStateFlow<SessionState>(SessionState.IDLE)
    val state: StateFlow<SessionState> = _state.asStateFlow()

    suspend fun prepare(context: Context, surfaceProvider: SurfaceProvider) {
        _state.value = SessionState.PREPARING

        try {
            pipelineManager.initialize(context, surfaceProvider)
            transportManager.initializeTransports()
            _state.value = SessionState.PREPARED
        } catch (e: Exception) {
            _state.value = SessionState.ERROR(StreamError.from(e))
        }
    }

    suspend fun start() {
        require(_state.value == SessionState.PREPARED) {
            "Session must be prepared before starting"
        }

        _state.value = SessionState.STREAMING

        try {
            transportManager.startAllTransports()
            pipelineManager.startStreaming()
        } catch (e: Exception) {
            _state.value = SessionState.ERROR(StreamError.from(e))
        }
    }
}
```

#### Transport Manager
传输管理器，负责多协议传输的协调：

```kotlin
class TransportManager {
    private val transports = mutableMapOf<TransportId, StreamTransport>()
    private val _primaryTransport = MutableStateFlow<TransportId?>(null)

    fun addTransport(config: TransportConfig): TransportId {
        val transport = TransportFactory.create(config)
        transports[config.id] = transport

        // 监听传输状态变化
        transport.state.onEach { state ->
            handleTransportStateChange(config.id, state)
        }.launchIn(scope)

        return config.id
    }

    suspend fun startAllTransports() {
        transports.values.forEach { transport ->
            launch { transport.connect() }
        }
    }

    suspend fun sendToAllActive(data: StreamData) {
        val activeTransports = transports.values.filter {
            it.state.value == TransportState.STREAMING
        }

        activeTransports.forEach { transport ->
            launch { transport.send(data) }
        }
    }
}
```

#### Configuration Manager
配置管理器，处理各种配置的验证和转换：

```kotlin
class ConfigurationManager {
    private var audioConfig: AudioConfig = AudioConfig()
    private var videoConfig: VideoConfig = VideoConfig()
    private var cameraConfig: CameraConfig = CameraConfig()

    fun updateAudioConfig(config: AudioConfig) {
        validateAudioConfig(config)
        audioConfig = config
        notifyConfigChange(ConfigType.AUDIO, config)
    }

    fun updateVideoConfig(config: VideoConfig) {
        validateVideoConfig(config)
        videoConfig = config
        notifyConfigChange(ConfigType.VIDEO, config)
    }

    private fun validateAudioConfig(config: AudioConfig) {
        require(config.sampleRate in listOf(8000, 16000, 44100, 48000)) {
            "Unsupported sample rate: ${config.sampleRate}"
        }
        require(config.bitrate in 32_000..320_000) {
            "Audio bitrate must be between 32k and 320k"
        }
    }
}
```

### 3. Infrastructure Layer（基础设施层）

#### Stream Transport Interface
传输抽象接口：

```kotlin
interface StreamTransport {
    val id: TransportId
    val protocol: TransportProtocol
    val state: StateFlow<TransportState>
    val stats: StateFlow<TransportStats>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun send(data: StreamData)

    fun updateBitrate(bitrate: Int)
    fun getConnectionQuality(): ConnectionQuality
}
```

#### RTMP Transport Implementation
RTMP传输实现：

```kotlin
class RtmpTransport(private val config: RtmpConfig) : StreamTransport {
    override val id = config.id
    override val protocol = TransportProtocol.RTMP

    private val _state = MutableStateFlow<TransportState>(TransportState.DISCONNECTED)
    override val state: StateFlow<TransportState> = _state.asStateFlow()

    private val streamSession = RtmpStreamSession() // 使用现有实现

    override suspend fun connect() {
        _state.value = TransportState.CONNECTING

        try {
            streamSession.setDataSource(config.pushUrl)
            streamSession.connect()
            _state.value = TransportState.CONNECTED
        } catch (e: Exception) {
            _state.value = TransportState.ERROR(TransportError.ConnectionFailed(
                message = e.message ?: "Connection failed",
                transport = protocol
            ))
        }
    }

    override suspend fun send(data: StreamData) {
        when (data) {
            is AudioData -> streamSession.sendAudio(data.bytes, data.timestamp)
            is VideoData -> streamSession.sendVideo(data.bytes, data.timestamp, data.isKeyFrame)
        }
    }
}
```

#### WebRTC Transport Implementation
WebRTC传输实现：

```kotlin
class WebRtcTransport(private val config: WebRtcConfig) : StreamTransport {
    override val id = config.id
    override val protocol = TransportProtocol.WEBRTC

    private val peerConnection: PeerConnection by lazy { createPeerConnection() }
    private val signalingClient: SignalingClient by lazy {
        SignalingClient(config.signalingUrl)
    }

    override suspend fun connect() {
        _state.value = TransportState.CONNECTING

        try {
            // 连接信令服务器
            signalingClient.connect()

            // 加入房间
            signalingClient.joinRoom(config.roomId)

            // 创建Offer
            val offer = peerConnection.createOffer()
            peerConnection.setLocalDescription(offer)
            signalingClient.sendOffer(offer)

            _state.value = TransportState.CONNECTED
        } catch (e: Exception) {
            _state.value = TransportState.ERROR(TransportError.ConnectionFailed(
                message = e.message ?: "WebRTC connection failed",
                transport = protocol
            ))
        }
    }

    override suspend fun send(data: StreamData) {
        // WebRTC通过PeerConnection发送数据
        when (data) {
            is AudioData -> sendAudioFrame(data)
            is VideoData -> sendVideoFrame(data)
        }
    }
}
```

## 数据流设计

### 音视频数据流

```
┌─────────────┐    ┌─────────────┐    ┌─────────────┐
│   Camera    │    │ Microphone  │    │   Screen    │
│   Capture   │    │   Capture   │    │   Capture   │
└─────────────┘    └─────────────┘    └─────────────┘
       │                   │                   │
       └───────────────────┼───────────────────┘
                           │
                    ┌─────────────┐
                    │ Data Source │
                    │  Multiplexer│
                    └─────────────┘
                           │
                    ┌─────────────┐
                    │ Preprocessing│
                    │   Pipeline  │
                    └─────────────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
     ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
     │   Video     │ │   Audio     │ │  Metadata   │
     │  Encoder    │ │  Encoder    │ │  Processor  │
     └─────────────┘ └─────────────┘ └─────────────┘
            │              │              │
            └──────────────┼──────────────┘
                           │
                    ┌─────────────┐
                    │  Transport  │
                    │   Manager   │
                    └─────────────┘
                           │
            ┌──────────────┼──────────────┐
            │              │              │
     ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
     │    RTMP     │ │   WebRTC    │ │     SRT     │
     │  Transport  │ │  Transport  │ │  Transport  │
     └─────────────┘ └─────────────┘ └─────────────┘
```

### Pipeline Manager
管道管理器，协调音视频数据流：

```kotlin
class PipelineManager(
    private val configManager: ConfigurationManager,
    private val transportManager: TransportManager
) {
    private val audioProcessor = AudioProcessor()
    private val videoProcessor = VideoProcessor()
    private val metadataProcessor = MetadataProcessor()

    fun startStreaming() {
        // 启动音频处理管道
        audioProcessor.start { audioData ->
            transportManager.sendToAllActive(audioData)
        }

        // 启动视频处理管道
        videoProcessor.start { videoData ->
            transportManager.sendToAllActive(videoData)
        }

        // 启动元数据处理
        metadataProcessor.start()
    }

    suspend fun processVideoFrame(frame: VideoFrame) {
        val processedFrame = videoProcessor.process(frame)
        val encodedData = videoProcessor.encode(processedFrame)

        // 并发发送到所有传输
        transportManager.sendToAllActive(encodedData)
    }
}
```

## 状态管理

### 状态机设计

```kotlin
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
```

### 状态同步机制

```kotlin
class StateManager {
    private val sessionState = MutableStateFlow<SessionState>(SessionState.IDLE)
    private val transportStates = mutableMapOf<TransportId, MutableStateFlow<TransportState>>()

    fun updateSessionState(newState: SessionState) {
        val currentState = sessionState.value
        require(currentState.canTransitionTo(newState)) {
            "Invalid state transition from $currentState to $newState"
        }
        sessionState.value = newState
    }

    fun syncStatesFromTransports() {
        val allTransportStates = transportStates.values.map { it.value }

        val newSessionState = when {
            allTransportStates.any { it is TransportState.STREAMING } ->
                SessionState.STREAMING
            allTransportStates.any { it is TransportState.CONNECTING } ->
                SessionState.PREPARING
            allTransportStates.all { it is TransportState.DISCONNECTED } ->
                SessionState.IDLE
            allTransportStates.any { it is TransportState.ERROR } ->
                SessionState.ERROR(deriveSessionError(allTransportStates))
            else -> SessionState.PREPARED
        }

        if (sessionState.value != newSessionState) {
            updateSessionState(newSessionState)
        }
    }
}
```

## 设计模式应用

### 1. Factory Pattern（工厂模式）

```kotlin
object TransportFactory {
    fun create(config: TransportConfig): StreamTransport {
        return when (config) {
            is RtmpConfig -> RtmpTransport(config)
            is WebRtcConfig -> WebRtcTransport(config)
            is SrtConfig -> SrtTransport(config)
            else -> throw UnsupportedTransportException(config::class.simpleName)
        }
    }
}

object EncoderFactory {
    fun createVideoEncoder(config: VideoConfig): VideoEncoder {
        return when {
            config.enableHardwareAcceleration -> HardwareVideoEncoder(config)
            else -> SoftwareVideoEncoder(config)
        }
    }
}
```

### 2. Observer Pattern（观察者模式）

```kotlin
class StreamEventBus {
    private val listeners = mutableSetOf<StreamEventListener>()

    fun subscribe(listener: StreamEventListener) {
        listeners.add(listener)
    }

    fun unsubscribe(listener: StreamEventListener) {
        listeners.remove(listener)
    }

    fun publish(event: StreamEvent) {
        listeners.forEach { it.onEvent(event) }
    }
}

interface StreamEventListener {
    fun onEvent(event: StreamEvent)
}
```

### 3. Strategy Pattern（策略模式）

```kotlin
interface BitrateStrategy {
    fun calculateBitrate(quality: ConnectionQuality, currentBitrate: Int): Int
}

class ConservativeStrategy : BitrateStrategy {
    override fun calculateBitrate(quality: ConnectionQuality, currentBitrate: Int): Int {
        return when (quality) {
            ConnectionQuality.EXCELLENT -> (currentBitrate * 1.1).toInt()
            ConnectionQuality.GOOD -> currentBitrate
            ConnectionQuality.FAIR -> (currentBitrate * 0.8).toInt()
            ConnectionQuality.POOR -> (currentBitrate * 0.5).toInt()
        }
    }
}

class AggressiveStrategy : BitrateStrategy {
    override fun calculateBitrate(quality: ConnectionQuality, currentBitrate: Int): Int {
        return when (quality) {
            ConnectionQuality.EXCELLENT -> (currentBitrate * 1.3).toInt()
            ConnectionQuality.GOOD -> (currentBitrate * 1.1).toInt()
            ConnectionQuality.FAIR -> (currentBitrate * 0.7).toInt()
            ConnectionQuality.POOR -> (currentBitrate * 0.3).toInt()
        }
    }
}
```

### 4. Adapter Pattern（适配器模式）

```kotlin
class LegacyApiAdapter(
    private val unifiedSession: UnifiedStreamSession
) : LiveStreamSession {

    override fun startLive() {
        runBlocking { unifiedSession.start() }
    }

    override fun stopLive() {
        runBlocking { unifiedSession.stop() }
    }

    override fun setVideoBps(bps: Int) {
        val currentConfig = unifiedSession.getVideoConfig()
        unifiedSession.updateVideoConfig(
            currentConfig.copy(bitrate = bps)
        )
    }

    override fun setOnLiveStateListener(listener: OnLiveStateListener) {
        unifiedSession.state
            .onEach { state ->
                when (state) {
                    SessionState.STREAMING -> listener.onLiveStart()
                    SessionState.IDLE -> listener.onLiveStop()
                    is SessionState.ERROR -> listener.onLiveError(state.error.message)
                    else -> { /* 其他状态 */ }
                }
            }
            .launchIn(GlobalScope)
    }
}
```

## 错误处理架构

### 错误分类体系

```kotlin
sealed class StreamError {
    abstract val code: String
    abstract val message: String
    abstract val recoverable: Boolean
    abstract val timestamp: Instant
}

// 传输层错误
sealed class TransportError : StreamError()

// 编码层错误
sealed class EncodingError : StreamError()

// 网络层错误
sealed class NetworkError : StreamError()

// 系统层错误
sealed class SystemError : StreamError()
```

### 错误恢复机制

```kotlin
class ErrorRecoveryManager {
    private val recoveryStrategies = mapOf<KClass<out StreamError>, RecoveryStrategy>(
        TransportError.ConnectionFailed::class to RetryStrategy(),
        EncodingError.HardwareEncoderFailed::class to FallbackStrategy(),
        NetworkError.TimeoutError::class to BackoffStrategy()
    )

    suspend fun handleError(error: StreamError): RecoveryResult {
        val strategy = recoveryStrategies[error::class]
        return strategy?.recover(error) ?: RecoveryResult.FAILED
    }
}

interface RecoveryStrategy {
    suspend fun recover(error: StreamError): RecoveryResult
}

class RetryStrategy : RecoveryStrategy {
    override suspend fun recover(error: StreamError): RecoveryResult {
        repeat(3) { attempt ->
            delay(1000 * (attempt + 1))
            try {
                // 重试逻辑
                return RecoveryResult.SUCCESS
            } catch (e: Exception) {
                if (attempt == 2) return RecoveryResult.FAILED
            }
        }
        return RecoveryResult.FAILED
    }
}
```

## 扩展机制

### 插件系统架构

```kotlin
interface StreamPlugin {
    val name: String
    val version: String
    val dependencies: List<String>

    fun initialize(context: PluginContext)
    fun onFrameProcessed(frame: Frame): Frame
    fun onStateChanged(state: SessionState)
    fun cleanup()
}

class PluginManager {
    private val plugins = mutableMapOf<String, StreamPlugin>()
    private val pluginContext = PluginContextImpl()

    fun registerPlugin(plugin: StreamPlugin) {
        validateDependencies(plugin)
        plugins[plugin.name] = plugin
        plugin.initialize(pluginContext)
    }

    fun processFrameWithPlugins(frame: Frame): Frame {
        return plugins.values.fold(frame) { currentFrame, plugin ->
            plugin.onFrameProcessed(currentFrame)
        }
    }
}
```

### 自定义传输协议

```kotlin
abstract class CustomTransport : StreamTransport {
    abstract val protocolName: String

    // 子类必须实现的核心方法
    abstract suspend fun establishConnection()
    abstract suspend fun sendData(data: ByteArray)
    abstract fun getProtocolSpecificStats(): Map<String, Any>

    // 提供默认实现的通用方法
    override suspend fun connect() {
        _state.value = TransportState.CONNECTING
        try {
            establishConnection()
            _state.value = TransportState.CONNECTED
        } catch (e: Exception) {
            _state.value = TransportState.ERROR(TransportError.from(e))
        }
    }
}

// 注册机制
object TransportRegistry {
    private val factories = mutableMapOf<String, (TransportConfig) -> StreamTransport>()

    fun registerTransport(protocol: String, factory: (TransportConfig) -> StreamTransport) {
        factories[protocol] = factory
    }

    fun createTransport(config: TransportConfig): StreamTransport {
        val factory = factories[config.protocol]
            ?: throw UnsupportedProtocolException(config.protocol)
        return factory(config)
    }
}
```

## 性能优化架构

### 内存管理

```kotlin
class MemoryManager {
    private val frameBufferPool = ObjectPool<ByteArray> { ByteArray(65536) }
    private val audioBufferPool = ObjectPool<ByteArray> { ByteArray(4096) }

    fun borrowVideoBuffer(): ByteArray = frameBufferPool.borrow()
    fun returnVideoBuffer(buffer: ByteArray) = frameBufferPool.return(buffer)

    fun borrowAudioBuffer(): ByteArray = audioBufferPool.borrow()
    fun returnAudioBuffer(buffer: ByteArray) = audioBufferPool.return(buffer)
}

class ObjectPool<T>(
    private val factory: () -> T,
    private val maxSize: Int = 20
) {
    private val pool = Channel<T>(capacity = maxSize)

    init {
        repeat(maxSize) { pool.trySend(factory()) }
    }

    fun borrow(): T = pool.tryReceive().getOrNull() ?: factory()

    fun return(item: T) {
        pool.trySend(item)
    }
}
```

### 并发处理

```kotlin
class ConcurrentProcessor {
    private val audioScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("AudioProcessor")
    )
    private val videoScope = CoroutineScope(
        Dispatchers.Default + SupervisorJob() + CoroutineName("VideoProcessor")
    )
    private val networkScope = CoroutineScope(
        Dispatchers.IO + SupervisorJob() + CoroutineName("NetworkSender")
    )

    fun startProcessing() {
        // 音频处理协程
        audioScope.launch {
            audioFrameFlow
                .flowOn(Dispatchers.Default)
                .buffer(capacity = 50, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .collect { frame -> processAudioFrame(frame) }
        }

        // 视频处理协程
        videoScope.launch {
            videoFrameFlow
                .flowOn(Dispatchers.Default)
                .buffer(capacity = 30, onBufferOverflow = BufferOverflow.DROP_OLDEST)
                .collect { frame -> processVideoFrame(frame) }
        }

        // 网络发送协程
        networkScope.launch {
            encodedDataFlow
                .flowOn(Dispatchers.IO)
                .collect { data -> sendToTransports(data) }
        }
    }
}
```

## 测试架构

### 测试分层

```kotlin
// 单元测试 - 测试单个组件
class SessionControllerTest {
    @Test
    fun `should transition to streaming state when start is called`() = runTest {
        val controller = SessionController(mockConfigManager, mockTransportManager, mockPipelineManager)

        controller.prepare(mockContext, mockSurfaceProvider)
        controller.start()

        assertEquals(SessionState.STREAMING, controller.state.value)
    }
}

// 集成测试 - 测试组件间交互
class TransportIntegrationTest {
    @Test
    fun `should send data to all active transports`() = runTest {
        val session = createTestSession()
        val rtmpTransport = mockk<StreamTransport>()
        val webrtcTransport = mockk<StreamTransport>()

        session.addTransport(createRtmpConfig())
        session.addTransport(createWebRtcConfig())

        val testData = VideoData(ByteArray(1024), 123456L, true)
        session.send(testData)

        verify { rtmpTransport.send(testData) }
        verify { webrtcTransport.send(testData) }
    }
}

// 端到端测试 - 测试完整流程
class EndToEndStreamingTest {
    @Test
    fun `should successfully stream to RTMP server`() = runTest {
        val session = createRealSession()
        session.addTransport(RtmpConfig(pushUrl = "rtmp://test-server/live/stream"))

        session.prepare(testContext, testSurfaceProvider)
        session.start()

        // 发送测试帧
        repeat(100) {
            session.sendTestFrame()
            delay(33) // 30fps
        }

        session.stop()

        // 验证服务器收到数据
        assertThat(testServer.receivedFrameCount).isGreaterThan(90)
    }
}
```

## 部署和监控

### 监控架构

```kotlin
class MetricsCollector {
    private val metrics = mutableMapOf<String, Metric>()

    fun recordLatency(operation: String, duration: Duration) {
        val metric = metrics.getOrPut(operation) { LatencyMetric() }
        metric.record(duration)
    }

    fun recordThroughput(operation: String, bytes: Long) {
        val metric = metrics.getOrPut(operation) { ThroughputMetric() }
        metric.record(bytes)
    }

    fun getMetrics(): Map<String, MetricSnapshot> {
        return metrics.mapValues { it.value.snapshot() }
    }
}

interface Metric {
    fun record(value: Any)
    fun snapshot(): MetricSnapshot
}

class LatencyMetric : Metric {
    private val samples = mutableListOf<Duration>()

    override fun record(value: Any) {
        if (value is Duration) {
            samples.add(value)
            if (samples.size > 1000) {
                samples.removeAt(0)
            }
        }
    }

    override fun snapshot(): MetricSnapshot {
        return LatencySnapshot(
            average = samples.map { it.inWholeMilliseconds }.average(),
            p95 = samples.map { it.inWholeMilliseconds }.sorted()[samples.size * 95 / 100],
            max = samples.maxOfOrNull { it.inWholeMilliseconds } ?: 0
        )
    }
}
```

这个架构设计文档为统一推流SDK提供了完整的技术蓝图，确保系统的可扩展性、可维护性和高性能。
