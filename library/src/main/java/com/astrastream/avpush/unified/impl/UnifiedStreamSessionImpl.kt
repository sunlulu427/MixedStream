package com.astrastream.avpush.unified.impl

import android.content.Context
import com.astrastream.avpush.unified.*
import com.astrastream.avpush.unified.builder.AdvancedConfig
import com.astrastream.avpush.unified.config.*
import com.astrastream.avpush.unified.error.StreamError
import com.astrastream.avpush.unified.error.SystemError
import com.astrastream.avpush.unified.media.DefaultMediaController
import com.astrastream.avpush.unified.media.MediaController
import com.astrastream.avpush.unified.transport.*
import com.astrastream.avpush.unified.transport.rtmp.RtmpTransportFactory
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 统一推流会话实现
 */
class UnifiedStreamSessionImpl(
    private var videoConfig: VideoConfig,
    private var audioConfig: AudioConfig,
    private var cameraConfig: CameraConfig,
    initialTransportConfigs: List<TransportConfig>,
    private val advancedConfig: AdvancedConfig
) : UnifiedStreamSession {

    // 状态管理
    private val _state = MutableStateFlow<SessionState>(SessionState.IDLE)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(createInitialStats())
    override val stats: StateFlow<StreamStats> = _stats.asStateFlow()

    // 传输管理
    private val transports = ConcurrentHashMap<TransportId, StreamTransport>()
    private val transportConfigs = ConcurrentHashMap<TransportId, TransportConfig>()
    private var primaryTransportId: TransportId? = null

    // 会话管理
    private val sessionScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var mediaController: MediaController? = null
    private var surfaceProvider: SurfaceProvider? = null
    private var eventListener: StreamEventListener? = null

    // 统计信息
    private val sessionStartTime = AtomicLong(0)
    private val totalBytesSent = AtomicLong(0)
    private val framesSent = AtomicLong(0)
    private val framesDropped = AtomicLong(0)

    init {
        // 注册传输工厂
        TransportRegistry.registerFactory(RtmpTransportFactory())

        // 初始化传输配置
        initialTransportConfigs.forEach { config ->
            this.transportConfigs[config.id] = config
        }

        // 设置主传输
        primaryTransportId = findPrimaryTransport(initialTransportConfigs)

        // 启动状态监控
        startStateMonitoring()
    }

    override suspend fun prepare(context: Context, surfaceProvider: SurfaceProvider) {
        if (!_state.value.canTransitionTo(SessionState.PREPARING)) {
            throw IllegalStateException("Cannot prepare from state ${_state.value}")
        }

        _state.value = SessionState.PREPARING
        this.surfaceProvider = surfaceProvider

        try {
            // 初始化媒体控制器
            mediaController = DefaultMediaController(
                videoConfig = videoConfig,
                audioConfig = audioConfig,
                cameraConfig = cameraConfig
            )

            // 准备媒体控制器
            mediaController?.prepare(context, surfaceProvider)

            // 创建传输实例
            createTransports()

            _state.value = SessionState.PREPARED
            notifyStateChanged(SessionState.PREPARED)

        } catch (e: Exception) {
            val error = StreamError.from(e)
            _state.value = SessionState.ERROR(error)
            notifyError(error)
            throw e
        }
    }

    override suspend fun start() {
        if (!_state.value.canTransitionTo(SessionState.STREAMING)) {
            throw IllegalStateException("Cannot start from state ${_state.value}")
        }

        sessionStartTime.set(System.currentTimeMillis())

        try {
            // 启动媒体控制器
            mediaController?.start()

            // 连接所有传输
            connectTransports()

            _state.value = SessionState.STREAMING
            notifyStateChanged(SessionState.STREAMING)

            // 启动统计信息收集
            startStatsCollection()

        } catch (e: Exception) {
            val error = StreamError.from(e)
            _state.value = SessionState.ERROR(error)
            notifyError(error)
            throw e
        }
    }

    override suspend fun stop() {
        if (!_state.value.canTransitionTo(SessionState.STOPPING)) {
            return
        }

        _state.value = SessionState.STOPPING

        try {
            // 停止媒体控制器
            mediaController?.stop()

            // 断开所有传输
            disconnectTransports()

            _state.value = SessionState.IDLE
            notifyStateChanged(SessionState.IDLE)

        } catch (e: Exception) {
            val error = StreamError.from(e)
            _state.value = SessionState.ERROR(error)
            notifyError(error)
        }
    }

    override suspend fun release() {
        sessionScope.cancel()

        try {
            // 释放媒体控制器
            mediaController?.release()

            // 释放所有传输
            transports.values.forEach { transport ->
                try {
                    transport.disconnect()
                } catch (e: Exception) {
                    // 忽略释放时的错误
                }
            }

            transports.clear()
            transportConfigs.clear()

            _state.value = SessionState.IDLE

        } catch (e: Exception) {
            // 忽略释放时的错误
        }
    }

    override fun addTransport(config: TransportConfig): TransportId {
        transportConfigs[config.id] = config

        // 如果会话已准备，立即创建传输实例
        if (_state.value in listOf(SessionState.PREPARED, SessionState.STREAMING)) {
            try {
                val transport = TransportRegistry.createTransport(config)
                transports[config.id] = transport
                setupTransportMonitoring(transport)

                // 如果正在推流，连接新传输
                if (_state.value == SessionState.STREAMING) {
                    sessionScope.launch {
                        try {
                            transport.connect()
                        } catch (e: Exception) {
                            // 记录错误但不影响主会话
                        }
                    }
                }
            } catch (e: Exception) {
                // 传输创建失败，记录错误
            }
        }

        // 更新主传输
        if (primaryTransportId == null || config.priority < getCurrentPrimaryPriority()) {
            primaryTransportId = config.id
        }

        return config.id
    }

    override fun removeTransport(transportId: TransportId) {
        transportConfigs.remove(transportId)

        transports[transportId]?.let { transport ->
            sessionScope.launch {
                try {
                    transport.disconnect()
                } catch (e: Exception) {
                    // 忽略断开连接错误
                }
            }
            transports.remove(transportId)
        }

        // 如果移除的是主传输，重新选择
        if (primaryTransportId == transportId) {
            primaryTransportId = findPrimaryTransport(transportConfigs.values.toList())
        }
    }

    override fun switchPrimaryTransport(transportId: TransportId) {
        if (transportConfigs.containsKey(transportId)) {
            primaryTransportId = transportId
        }
    }

    override fun updateVideoConfig(config: VideoConfig) {
        videoConfig = config
        mediaController?.updateVideoConfig(config)
    }

    override fun updateAudioConfig(config: AudioConfig) {
        audioConfig = config
        mediaController?.updateAudioConfig(config)
    }

    override fun observeTransportState(transportId: TransportId): Flow<TransportState> {
        return transports[transportId]?.state ?: flowOf(TransportState.DISCONNECTED)
    }

    override fun observeConnectionQuality(): Flow<ConnectionQuality> {
        return combine(
            transports.values.map { it.state }
        ) { states ->
            calculateOverallConnectionQuality(states.toList())
        }.distinctUntilChanged()
    }

    override fun getAllTransportStats(): Flow<Map<TransportId, TransportStats>> {
        return combine(
            transports.map { (id, transport) ->
                transport.stats.map { id to it }
            }
        ) { statsArray ->
            statsArray.toMap()
        }
    }

    override fun setWatermark(watermark: Watermark) {
        mediaController?.setWatermark(watermark)
    }

    override fun setEventListener(listener: StreamEventListener) {
        eventListener = listener
    }

    private fun createTransports() {
        transportConfigs.values.forEach { config ->
            try {
                val transport = TransportRegistry.createTransport(config)
                transports[config.id] = transport
                setupTransportMonitoring(transport)
            } catch (e: Exception) {
                // 传输创建失败，记录错误但继续
            }
        }
    }

    private suspend fun connectTransports() {
        val connectJobs = transports.values.map { transport ->
            sessionScope.async {
                try {
                    transport.connect()
                } catch (e: Exception) {
                    // 单个传输连接失败不影响其他传输
                }
            }
        }

        // 等待所有传输连接完成
        connectJobs.awaitAll()

        // 检查是否至少有一个传输成功连接
        val hasConnectedTransport = transports.values.any {
            it.state.value in listOf(TransportState.CONNECTED, TransportState.STREAMING)
        }

        if (!hasConnectedTransport) {
            throw RuntimeException("No transport could be connected")
        }
    }

    private suspend fun disconnectTransports() {
        val disconnectJobs = transports.values.map { transport ->
            sessionScope.async {
                try {
                    transport.disconnect()
                } catch (e: Exception) {
                    // 忽略断开连接错误
                }
            }
        }

        disconnectJobs.awaitAll()
    }

    private suspend fun sendDataToTransports(audioData: AudioData?, videoData: VideoData?) {
        val activeTransports = getActiveTransports()

        if (advancedConfig.enableSimultaneousPush) {
            // 并发发送到所有活跃传输
            val sendJobs = activeTransports.map { transport ->
                sessionScope.async {
                    try {
                        audioData?.let { transport.sendAudioData(it) }
                        videoData?.let { transport.sendVideoData(it) }

                        if (videoData != null) {
                            framesSent.incrementAndGet()
                            totalBytesSent.addAndGet(videoData.bytes.size.toLong())
                        }
                        if (audioData != null) {
                            totalBytesSent.addAndGet(audioData.bytes.size.toLong())
                        }
                    } catch (e: Exception) {
                        framesDropped.incrementAndGet()
                        // 处理发送错误
                        handleTransportError(transport.id, e)
                    }
                }
            }
            sendJobs.awaitAll()
        } else {
            // 发送到主传输
            val primaryTransport = getPrimaryTransport()
            if (primaryTransport != null) {
                try {
                    audioData?.let { primaryTransport.sendAudioData(it) }
                    videoData?.let { primaryTransport.sendVideoData(it) }

                    if (videoData != null) {
                        framesSent.incrementAndGet()
                        totalBytesSent.addAndGet(videoData.bytes.size.toLong())
                    }
                    if (audioData != null) {
                        totalBytesSent.addAndGet(audioData.bytes.size.toLong())
                    }
                } catch (e: Exception) {
                    framesDropped.incrementAndGet()
                    handleTransportError(primaryTransport.id, e)
                }
            }
        }
    }

    private fun setupTransportMonitoring(transport: StreamTransport) {
        sessionScope.launch {
            transport.state.collect { state ->
                notifyTransportStateChanged(transport.id, state)

                // 处理传输错误
                if (state is TransportState.ERROR) {
                    handleTransportError(transport.id, RuntimeException(state.error.message))
                }
            }
        }
    }

    private fun getActiveTransports(): List<StreamTransport> {
        return transports.values.filter {
            it.state.value in listOf(TransportState.CONNECTED, TransportState.STREAMING)
        }
    }

    private fun getPrimaryTransport(): StreamTransport? {
        return primaryTransportId?.let { transports[it] }
    }

    private fun findPrimaryTransport(configs: List<TransportConfig>): TransportId? {
        return configs.minByOrNull { it.priority }?.id
    }

    private fun getCurrentPrimaryPriority(): Int {
        return primaryTransportId?.let { transportConfigs[it]?.priority } ?: Int.MAX_VALUE
    }

    private fun calculateOverallConnectionQuality(states: List<TransportState>): ConnectionQuality {
        val activeStates = states.filter {
            it in listOf(TransportState.CONNECTED, TransportState.STREAMING)
        }

        return when {
            activeStates.isEmpty() -> ConnectionQuality.POOR
            activeStates.size == states.size -> ConnectionQuality.EXCELLENT
            activeStates.size >= states.size / 2 -> ConnectionQuality.GOOD
            else -> ConnectionQuality.FAIR
        }
    }

    private fun handleTransportError(transportId: TransportId, error: Exception) {
        // 如果启用了故障转移且有备用传输
        if (advancedConfig.fallbackEnabled && transportId == primaryTransportId) {
            val fallbackTransport = findFallbackTransport()
            if (fallbackTransport != null) {
                switchPrimaryTransport(fallbackTransport.id)
            }
        }
    }

    private fun findFallbackTransport(): StreamTransport? {
        return transports.values
            .filter { it.id != primaryTransportId }
            .filter { it.state.value in listOf(TransportState.CONNECTED, TransportState.STREAMING) }
            .minByOrNull { transportConfigs[it.id]?.priority ?: Int.MAX_VALUE }
    }

    private fun startStateMonitoring() {
        sessionScope.launch {
            state.collect { newState ->
                // 状态变化处理逻辑
            }
        }
    }

    private fun startStatsCollection() {
        sessionScope.launch {
            while (isActive && _state.value == SessionState.STREAMING) {
                updateStats()
                delay(advancedConfig.statsInterval.toMillis())
            }
        }
    }

    private fun updateStats() {
        val sessionDuration = if (sessionStartTime.get() > 0) {
            Duration.ofMillis(System.currentTimeMillis() - sessionStartTime.get())
        } else {
            Duration.ZERO
        }

        val averageBitrate = if (sessionDuration.toMillis() > 0) {
            ((totalBytesSent.get() * 8 * 1000) / sessionDuration.toMillis()).toInt()
        } else {
            0
        }

        _stats.value = StreamStats(
            sessionDuration = sessionDuration,
            totalBytesSent = totalBytesSent.get(),
            averageBitrate = averageBitrate,
            currentFps = calculateCurrentFps(),
            framesSent = framesSent.get(),
            framesDropped = framesDropped.get(),
            activeTransports = getActiveTransports().size,
            connectionQuality = calculateOverallConnectionQuality(
                transports.values.map { it.state.value }
            ),
            cpuUsage = 0.0, // TODO: 实现CPU使用率监控
            memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        )
    }

    private fun calculateCurrentFps(): Int {
        // 简单的FPS计算，实际实现可能需要更复杂的逻辑
        return videoConfig.frameRate
    }

    private fun createInitialStats(): StreamStats {
        return StreamStats(
            sessionDuration = Duration.ZERO,
            totalBytesSent = 0,
            averageBitrate = 0,
            currentFps = 0,
            framesSent = 0,
            framesDropped = 0,
            activeTransports = 0,
            connectionQuality = ConnectionQuality.POOR,
            cpuUsage = 0.0,
            memoryUsage = 0
        )
    }

    private fun notifyStateChanged(state: SessionState) {
        eventListener?.onSessionStateChanged(state)
    }

    private fun notifyTransportStateChanged(transportId: TransportId, state: TransportState) {
        eventListener?.onTransportStateChanged(transportId, state)
    }

    private fun notifyError(error: StreamError) {
        eventListener?.onError(error)
    }
}

/**
 * 媒体控制器接口
 *
 * 这是一个简化的接口定义，实际实现需要集成现有的音视频管道
 */
interface MediaController {
    suspend fun prepare()
    suspend fun start(dataCallback: (AudioData?, VideoData?) -> Unit)
    suspend fun stop()
    suspend fun release()
    fun updateVideoConfig(config: VideoConfig)
    fun updateAudioConfig(config: AudioConfig)
    fun setWatermark(watermark: Watermark)
}