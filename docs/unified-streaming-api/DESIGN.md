# 统一推流SDK详细设计文档

## 1. 设计原则

### 1.1 Clean Architecture
遵循Clean Architecture原则，确保依赖关系向内流动：

```
Application Layer (UI/Widget)
    ↓
Use Case Layer (Session Management)
    ↓
Infrastructure Layer (Transport Implementations)
```

### 1.2 依赖倒置原则
- 高层策略（Session管理）不依赖低层实现细节（具体传输协议）
- 所有跨层交互通过接口进行
- 便于单元测试和模块替换

### 1.3 单一职责原则
- 每个组件专注于特定功能
- 传输层只负责数据传输
- 会话层只负责协调管理
- 配置层只负责参数管理

## 2. 核心接口设计

### 2.1 统一会话接口

```kotlin
interface UnifiedStreamSession {
    val state: StateFlow<SessionState>
    val stats: StateFlow<StreamStats>

    suspend fun prepare(context: Context, surfaceProvider: SurfaceProvider)
    suspend fun start()
    suspend fun stop()
    suspend fun release()

    // 传输管理
    fun addTransport(config: TransportConfig): TransportId
    fun removeTransport(transportId: TransportId)
    fun switchPrimaryTransport(transportId: TransportId)

    // 配置更新
    fun updateVideoConfig(config: VideoConfig)
    fun updateAudioConfig(config: AudioConfig)

    // 状态监听
    fun observeTransportState(transportId: TransportId): Flow<TransportState>
    fun observeConnectionQuality(): Flow<ConnectionQuality>
    fun getAllTransportStats(): Flow<Map<TransportId, TransportStats>>
}
```

### 2.2 传输抽象接口

```kotlin
interface StreamTransport {
    val id: TransportId
    val protocol: TransportProtocol
    val state: StateFlow<TransportState>
    val stats: StateFlow<TransportStats>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun sendAudioData(data: ByteArray, timestamp: Long)
    suspend fun sendVideoData(data: ByteArray, timestamp: Long, isKeyFrame: Boolean)

    fun updateBitrate(bitrate: Int)
    fun getConnectionQuality(): ConnectionQuality
}
```

### 2.3 配置系统设计

```kotlin
// 密封类确保类型安全
sealed class TransportConfig {
    abstract val id: TransportId
    abstract val priority: Int
    abstract val enabled: Boolean
}

data class RtmpConfig(
    override val id: TransportId = TransportId.generate(),
    override val priority: Int = 1,
    override val enabled: Boolean = true,
    val pushUrl: String,
    val connectTimeout: Duration = 5.seconds,
    val retryPolicy: RetryPolicy = RetryPolicy.exponentialBackoff()
) : TransportConfig()

data class WebRtcConfig(
    override val id: TransportId = TransportId.generate(),
    override val priority: Int = 0, // 更高优先级
    override val enabled: Boolean = true,
    val signalingUrl: String,
    val roomId: String,
    val iceServers: List<IceServer> = emptyList(),
    val audioCodec: AudioCodec = AudioCodec.OPUS,
    val videoCodec: VideoCodec = VideoCodec.H264
) : TransportConfig()
```

## 3. 状态管理系统

### 3.1 会话状态

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

### 3.2 传输状态

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

### 3.3 响应式状态更新

```kotlin
class UnifiedStreamSessionImpl : UnifiedStreamSession {
    private val _state = MutableStateFlow<SessionState>(SessionState.IDLE)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val transportStates = mutableMapOf<TransportId, MutableStateFlow<TransportState>>()

    override fun observeTransportState(transportId: TransportId): Flow<TransportState> {
        return transportStates[transportId]?.asStateFlow()
            ?: flowOf(TransportState.DISCONNECTED)
    }

    // 自动状态同步
    private fun syncSessionState() {
        val allStates = transportStates.values.map { it.value }
        _state.value = when {
            allStates.any { it is TransportState.STREAMING } -> SessionState.STREAMING
            allStates.any { it is TransportState.CONNECTING } -> SessionState.PREPARING
            allStates.all { it is TransportState.DISCONNECTED } -> SessionState.IDLE
            else -> SessionState.PREPARED
        }
    }
}
```

## 4. 多协议传输管理

### 4.1 传输管理器

```kotlin
class TransportManager {
    private val transports = mutableMapOf<TransportId, StreamTransport>()
    private val _primaryTransport = MutableStateFlow<TransportId?>(null)
    val primaryTransport: StateFlow<TransportId?> = _primaryTransport.asStateFlow()

    fun addTransport(config: TransportConfig): StreamTransport {
        val transport = createTransport(config)
        transports[config.id] = transport

        // 自动设置为主传输（如果是第一个或优先级更高）
        if (_primaryTransport.value == null ||
            config.priority < getCurrentPrimaryPriority()) {
            _primaryTransport.value = config.id
        }

        return transport
    }

    private fun createTransport(config: TransportConfig): StreamTransport {
        return when (config) {
            is RtmpConfig -> RtmpTransport(config)
            is WebRtcConfig -> WebRtcTransport(config)
            // 可扩展支持更多协议
        }
    }

    suspend fun sendToAllActiveTransports(data: StreamData) {
        transports.values
            .filter { it.state.value is TransportState.STREAMING }
            .forEach { transport ->
                try {
                    when (data) {
                        is AudioData -> transport.sendAudioData(data.bytes, data.timestamp)
                        is VideoData -> transport.sendVideoData(data.bytes, data.timestamp, data.isKeyFrame)
                    }
                } catch (e: Exception) {
                    handleTransportError(transport.id, e)
                }
            }
    }
}
```

### 4.2 智能切换机制

```kotlin
class IntelligentSwitchingManager(
    private val transportManager: TransportManager,
    private val qualityMonitor: ConnectionQualityMonitor
) {

    fun startMonitoring() {
        qualityMonitor.observeQuality()
            .distinctUntilChanged()
            .onEach { quality ->
                when (quality) {
                    ConnectionQuality.POOR -> switchToFallback()
                    ConnectionQuality.GOOD -> switchToPrimary()
                    ConnectionQuality.EXCELLENT -> optimizeForQuality()
                }
            }
            .launchIn(scope)
    }

    private suspend fun switchToFallback() {
        val fallbackTransport = findBestFallbackTransport()
        fallbackTransport?.let {
            transportManager.setPrimaryTransport(it.id)
            logger.info("Switched to fallback transport: ${it.protocol}")
        }
    }

    private fun findBestFallbackTransport(): StreamTransport? {
        return transportManager.getAllTransports()
            .filter { it.state.value is TransportState.CONNECTED }
            .filter { it.getConnectionQuality() != ConnectionQuality.POOR }
            .minByOrNull { it.protocol.latency } // 选择延迟最低的
    }
}
```

## 5. 数据流管道设计

### 5.1 音视频数据流

```kotlin
interface StreamDataPipeline {
    suspend fun processAudioFrame(frame: AudioFrame): AudioData
    suspend fun processVideoFrame(frame: VideoFrame): VideoData
}

class UnifiedDataPipeline(
    private val audioProcessor: AudioProcessor,
    private val videoProcessor: VideoProcessor,
    private val transportManager: TransportManager
) : StreamDataPipeline {

    override suspend fun processAudioFrame(frame: AudioFrame): AudioData {
        val processedFrame = audioProcessor.process(frame)
        val encodedData = audioProcessor.encode(processedFrame)

        // 并发发送到所有活跃传输
        transportManager.sendToAllActiveTransports(encodedData)

        return encodedData
    }

    override suspend fun processVideoFrame(frame: VideoFrame): VideoData {
        val processedFrame = videoProcessor.process(frame)
        val encodedData = videoProcessor.encode(processedFrame)

        transportManager.sendToAllActiveTransports(encodedData)

        return encodedData
    }
}
```

### 5.2 背压处理

```kotlin
class BackpressureHandler {
    private val audioBuffer = Channel<AudioFrame>(capacity = 50)
    private val videoBuffer = Channel<VideoFrame>(capacity = 30)

    suspend fun handleAudioFrame(frame: AudioFrame) {
        when (audioBuffer.trySend(frame)) {
            is ChannelResult.Success -> { /* 成功 */ }
            is ChannelResult.Failure -> {
                // 丢弃最老的帧
                audioBuffer.tryReceive()
                audioBuffer.trySend(frame)
                metrics.incrementDroppedAudioFrames()
            }
        }
    }

    suspend fun handleVideoFrame(frame: VideoFrame) {
        when (videoBuffer.trySend(frame)) {
            is ChannelResult.Success -> { /* 成功 */ }
            is ChannelResult.Failure -> {
                if (!frame.isKeyFrame) {
                    // 只丢弃非关键帧
                    metrics.incrementDroppedVideoFrames()
                } else {
                    // 关键帧强制清空缓冲区
                    videoBuffer.tryReceive()
                    videoBuffer.trySend(frame)
                }
            }
        }
    }
}
```

## 6. 错误处理与恢复

### 6.1 分层错误处理

```kotlin
sealed class StreamError {
    abstract val code: String
    abstract val message: String
    abstract val recoverable: Boolean
}

// 传输层错误
sealed class TransportError : StreamError() {
    data class ConnectionFailed(
        override val code: String = "TRANSPORT_CONNECTION_FAILED",
        override val message: String,
        val transport: TransportProtocol
    ) : TransportError() {
        override val recoverable: Boolean = true
    }

    data class AuthenticationFailed(
        override val code: String = "TRANSPORT_AUTH_FAILED",
        override val message: String
    ) : TransportError() {
        override val recoverable: Boolean = false
    }
}

// 编码错误
sealed class EncodingError : StreamError() {
    data class HardwareEncoderFailed(
        override val code: String = "HARDWARE_ENCODER_FAILED",
        override val message: String
    ) : EncodingError() {
        override val recoverable: Boolean = true // 可切换到软编码
    }
}
```

### 6.2 自动恢复机制

```kotlin
class ErrorRecoveryManager {
    suspend fun handleError(error: StreamError, context: StreamContext) {
        when {
            error.recoverable -> attemptRecovery(error, context)
            else -> notifyUnrecoverableError(error)
        }
    }

    private suspend fun attemptRecovery(error: StreamError, context: StreamContext) {
        when (error) {
            is TransportError.ConnectionFailed -> {
                retryConnection(error.transport, context)
            }
            is EncodingError.HardwareEncoderFailed -> {
                switchToSoftwareEncoder(context)
            }
        }
    }

    private suspend fun retryConnection(
        protocol: TransportProtocol,
        context: StreamContext
    ) {
        val retryPolicy = context.getRetryPolicy(protocol)
        repeat(retryPolicy.maxRetries) { attempt ->
            delay(retryPolicy.getDelay(attempt))

            try {
                context.reconnectTransport(protocol)
                return // 成功恢复
            } catch (e: Exception) {
                if (attempt == retryPolicy.maxRetries - 1) {
                    // 最后一次尝试失败，切换到备用传输
                    context.switchToFallbackTransport()
                }
            }
        }
    }
}
```

## 7. 性能优化设计

### 7.1 内存池管理

```kotlin
class FrameBufferPool {
    private val audioBufferPool = Channel<ByteArray>(capacity = 20)
    private val videoBufferPool = Channel<ByteArray>(capacity = 15)

    init {
        // 预分配缓冲区
        repeat(20) { audioBufferPool.trySend(ByteArray(4096)) }
        repeat(15) { videoBufferPool.trySend(ByteArray(65536)) }
    }

    suspend fun borrowAudioBuffer(): ByteArray {
        return audioBufferPool.tryReceive().getOrNull() ?: ByteArray(4096)
    }

    suspend fun returnAudioBuffer(buffer: ByteArray) {
        audioBufferPool.trySend(buffer)
    }
}
```

### 7.2 并发优化

```kotlin
class ConcurrentStreamProcessor {
    private val audioScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val videoScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val networkScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun startProcessing() {
        // 音频处理协程
        audioScope.launch {
            audioFrameFlow
                .flowOn(Dispatchers.Default)
                .collect { frame -> processAudioFrame(frame) }
        }

        // 视频处理协程
        videoScope.launch {
            videoFrameFlow
                .flowOn(Dispatchers.Default)
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

## 8. 向后兼容适配

### 8.1 适配器模式实现

```kotlin
class LegacyStreamSessionAdapter(
    private val unifiedSession: UnifiedStreamSession
) : LiveStreamSession {

    override fun startLive() {
        // 转换为新API调用
        GlobalScope.launch {
            unifiedSession.start()
        }
    }

    override fun stopLive() {
        GlobalScope.launch {
            unifiedSession.stop()
        }
    }

    override fun setVideoBps(bps: Int) {
        val currentConfig = unifiedSession.getVideoConfig()
        unifiedSession.updateVideoConfig(
            currentConfig.copy(bitrate = bps)
        )
    }

    // 状态转换
    override fun setOnLiveStateListener(listener: OnLiveStateListener) {
        unifiedSession.state
            .onEach { state ->
                when (state) {
                    SessionState.STREAMING -> listener.onLiveStart()
                    SessionState.IDLE -> listener.onLiveStop()
                    is SessionState.ERROR -> listener.onLiveError(state.error.message)
                }
            }
            .launchIn(GlobalScope)
    }
}
```

## 9. 扩展点设计

### 9.1 插件系统

```kotlin
interface StreamPlugin {
    val name: String
    val version: String

    fun initialize(context: PluginContext)
    fun onFrameProcessed(frame: Frame): Frame
    fun onStateChanged(state: SessionState)
    fun cleanup()
}

class PluginManager {
    private val plugins = mutableListOf<StreamPlugin>()

    fun registerPlugin(plugin: StreamPlugin) {
        plugins.add(plugin)
        plugin.initialize(createPluginContext())
    }

    fun processFrameWithPlugins(frame: Frame): Frame {
        return plugins.fold(frame) { currentFrame, plugin ->
            plugin.onFrameProcessed(currentFrame)
        }
    }
}
```

### 9.2 自定义传输协议

```kotlin
abstract class CustomTransport : StreamTransport {
    abstract val protocolName: String

    // 子类实现具体协议逻辑
    abstract suspend fun establishConnection()
    abstract suspend fun sendData(data: ByteArray)
    abstract fun getProtocolSpecificStats(): Map<String, Any>
}

// 注册自定义传输
class TransportRegistry {
    private val factories = mutableMapOf<String, TransportFactory>()

    fun registerTransport(name: String, factory: TransportFactory) {
        factories[name] = factory
    }

    fun createTransport(config: TransportConfig): StreamTransport {
        val factory = factories[config.protocol.name]
            ?: throw UnsupportedProtocolException(config.protocol.name)
        return factory.create(config)
    }
}
```

## 10. 测试策略

### 10.1 单元测试

```kotlin
class UnifiedStreamSessionTest {
    @Test
    fun `should switch transport when connection quality degrades`() = runTest {
        val session = createTestSession()
        val rtmpTransport = mockk<StreamTransport>()
        val webrtcTransport = mockk<StreamTransport>()

        // 模拟WebRTC连接质量下降
        every { webrtcTransport.getConnectionQuality() } returns ConnectionQuality.POOR
        every { rtmpTransport.getConnectionQuality() } returns ConnectionQuality.GOOD

        session.addTransport(createRtmpConfig())
        session.addTransport(createWebRtcConfig())

        // 触发质量检查
        session.checkConnectionQuality()

        // 验证切换到RTMP
        verify { session.switchPrimaryTransport(rtmpTransport.id) }
    }
}
```

### 10.2 集成测试

```kotlin
class MultiProtocolIntegrationTest {
    @Test
    fun `should stream simultaneously to RTMP and WebRTC`() = runTest {
        val session = createRealSession()

        session.addTransport(RtmpConfig(pushUrl = "rtmp://test-server/live/stream"))
        session.addTransport(WebRtcConfig(signalingUrl = "ws://test-signal", roomId = "test"))

        session.prepare(testContext, mockSurfaceProvider)
        session.start()

        // 发送测试帧
        repeat(100) {
            session.sendTestFrame()
            delay(33) // 30fps
        }

        // 验证两个传输都收到数据
        assertThat(rtmpTransport.sentFrameCount).isGreaterThan(90)
        assertThat(webrtcTransport.sentFrameCount).isGreaterThan(90)
    }
}
```

这个详细设计文档涵盖了统一推流SDK的核心技术实现，为开发团队提供了完整的技术指导。