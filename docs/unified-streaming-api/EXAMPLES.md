# 使用示例

本文档提供AVRtmpPushSDK统一推流接口的详细使用示例，涵盖从基础推流到高级功能的各种使用场景。

## 目录

1. [基础推流示例](#基础推流示例)
2. [多协议推流](#多协议推流)
3. [智能切换和自适应](#智能切换和自适应)
4. [高级配置](#高级配置)
5. [错误处理](#错误处理)
6. [性能监控](#性能监控)
7. [实际应用场景](#实际应用场景)

## 基础推流示例

### 1. 最简单的RTMP推流

```kotlin
class BasicStreamingActivity : AppCompatActivity() {
    private lateinit var session: UnifiedStreamSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_streaming)

        // 创建最基础的推流会话
        session = createStreamSession {
            addRtmp("rtmp://live.example.com/live/stream")
        }

        // 开始推流
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            lifecycleScope.launch {
                session.prepare(this@BasicStreamingActivity, surfaceProvider)
                session.start()
            }
        }

        // 停止推流
        findViewById<Button>(R.id.btn_stop).setOnClickListener {
            lifecycleScope.launch {
                session.stop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            session.release()
        }
    }
}
```

### 2. 自定义音视频参数

```kotlin
val session = createStreamSession {
    // 视频配置
    video {
        width = 1920
        height = 1080
        frameRate = 30
        bitrate = 4_000_000
        keyFrameInterval = 2
        enableAdaptiveBitrate = true
        codec = VideoCodec.H264
        profile = VideoProfile.HIGH
    }

    // 音频配置
    audio {
        sampleRate = 48000
        bitrate = 192_000
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
        enableFlash = false
        stabilization = true
        exposureMode = ExposureMode.AUTO
        whiteBalanceMode = WhiteBalanceMode.AUTO
    }

    addRtmp("rtmp://live.example.com/live/stream")
}
```

### 3. 带水印的推流

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
    }

    addRtmp("rtmp://live.example.com/live/stream")
}

// 设置文字水印
session.setWatermark(
    TextWatermark(
        text = "Live Stream",
        color = Color.WHITE,
        textSize = 48f,
        position = WatermarkPosition.TOP_RIGHT,
        margin = 20
    )
)

// 设置图片水印
session.setWatermark(
    ImageWatermark(
        bitmap = BitmapFactory.decodeResource(resources, R.drawable.logo),
        position = WatermarkPosition.BOTTOM_LEFT,
        margin = 20,
        alpha = 0.8f
    )
)
```

## 多协议推流

### 1. RTMP + WebRTC 同时推流

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

    // 添加RTMP推流
    addRtmp("rtmp://live.example.com/live/stream") {
        connectTimeout = 10.seconds
        retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 5)
        enableLowLatency = false
    }

    // 添加WebRTC推流
    addWebRtc("wss://signal.example.com", "room123") {
        audioCodec = AudioCodec.OPUS
        videoCodec = VideoCodec.H264
        maxBitrate = 3_000_000
        iceServers = listOf(
            IceServer("stun:stun.l.google.com:19302"),
            IceServer("turn:turn.example.com:3478", "username", "password")
        )
    }

    // 高级配置
    advanced {
        enableSimultaneousPush = true
        primaryTransport = TransportProtocol.WEBRTC
        fallbackEnabled = true
        fallbackTransports = listOf(TransportProtocol.RTMP)
    }
}
```

### 2. 多路RTMP推流（多平台直播）

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
    }

    // 推流到YouTube
    addRtmp("rtmp://a.rtmp.youtube.com/live2/YOUR_STREAM_KEY") {
        priority = 1
    }

    // 推流到Twitch
    addRtmp("rtmp://live.twitch.tv/live/YOUR_STREAM_KEY") {
        priority = 2
    }

    // 推流到Facebook
    addRtmp("rtmps://live-api-s.facebook.com:443/rtmp/YOUR_STREAM_KEY") {
        priority = 3
    }

    advanced {
        enableSimultaneousPush = true
    }
}

// 监听各个平台的推流状态
lifecycleScope.launch {
    session.getAllTransportStats().collect { statsMap ->
        statsMap.forEach { (transportId, stats) ->
            when (stats.protocol) {
                TransportProtocol.RTMP -> {
                    updatePlatformStatus(getPlatformName(transportId), stats.state)
                }
            }
        }
    }
}
```

## 智能切换和自适应

### 1. 基于网络质量的智能切换

```kotlin
class IntelligentStreamingActivity : AppCompatActivity() {
    private lateinit var session: UnifiedStreamSession
    private var rtmpTransportId: TransportId? = null
    private var webrtcTransportId: TransportId? = null

    private fun setupIntelligentSwitching() {
        session = createStreamSession {
            video {
                width = 1280
                height = 720
                frameRate = 30
                bitrate = 2_000_000
                enableAdaptiveBitrate = true
            }

            audio {
                sampleRate = 44100
                bitrate = 128_000
            }

            // 主要传输：WebRTC（低延迟）
            webrtcTransportId = addWebRtc("wss://signal.example.com", "room123") {
                priority = 0 // 最高优先级
            }.id

            // 备用传输：RTMP（稳定性好）
            rtmpTransportId = addRtmp("rtmp://live.example.com/live/stream") {
                priority = 1
            }.id

            advanced {
                primaryTransport = TransportProtocol.WEBRTC
                fallbackEnabled = true
                autoSwitchThreshold = ConnectionQuality.FAIR
            }
        }

        // 监听连接质量并自动切换
        lifecycleScope.launch {
            session.observeConnectionQuality().collect { quality ->
                handleQualityChange(quality)
            }
        }
    }

    private suspend fun handleQualityChange(quality: ConnectionQuality) {
        when (quality) {
            ConnectionQuality.EXCELLENT, ConnectionQuality.GOOD -> {
                // 网络良好，使用WebRTC
                webrtcTransportId?.let { session.switchPrimaryTransport(it) }
                showQualityIndicator("优秀", Color.GREEN)
            }
            ConnectionQuality.FAIR -> {
                // 网络一般，降低码率但保持WebRTC
                val currentConfig = session.getVideoConfig()
                session.updateVideoConfig(
                    currentConfig.copy(bitrate = (currentConfig.bitrate * 0.7).toInt())
                )
                showQualityIndicator("一般", Color.YELLOW)
            }
            ConnectionQuality.POOR -> {
                // 网络差，切换到RTMP
                rtmpTransportId?.let { session.switchPrimaryTransport(it) }
                val currentConfig = session.getVideoConfig()
                session.updateVideoConfig(
                    currentConfig.copy(bitrate = (currentConfig.bitrate * 0.5).toInt())
                )
                showQualityIndicator("较差", Color.RED)
            }
        }
    }

    private fun showQualityIndicator(quality: String, color: Int) {
        findViewById<TextView>(R.id.tv_quality).apply {
            text = "网络质量: $quality"
            setTextColor(color)
        }
    }
}
```

### 2. 自适应码率控制

```kotlin
class AdaptiveBitrateManager(private val session: UnifiedStreamSession) {
    private val targetQualities = mapOf(
        ConnectionQuality.EXCELLENT to BitrateProfile(4_000_000, 30, 1920, 1080),
        ConnectionQuality.GOOD to BitrateProfile(2_000_000, 30, 1280, 720),
        ConnectionQuality.FAIR to BitrateProfile(1_000_000, 25, 854, 480),
        ConnectionQuality.POOR to BitrateProfile(500_000, 20, 640, 360)
    )

    fun startAdaptation() {
        GlobalScope.launch {
            session.observeConnectionQuality()
                .distinctUntilChanged()
                .collect { quality ->
                    adaptBitrate(quality)
                }
        }
    }

    private suspend fun adaptBitrate(quality: ConnectionQuality) {
        val profile = targetQualities[quality] ?: return
        val currentConfig = session.getVideoConfig()

        // 平滑调整码率，避免突变
        val targetBitrate = profile.bitrate
        val currentBitrate = currentConfig.bitrate
        val step = (targetBitrate - currentBitrate) / 5

        repeat(5) { i ->
            val newBitrate = currentBitrate + step * (i + 1)
            session.updateVideoConfig(
                currentConfig.copy(
                    bitrate = newBitrate.toInt(),
                    frameRate = profile.frameRate
                )
            )
            delay(1000) // 每秒调整一次
        }

        // 如果质量持续很差，考虑降低分辨率
        if (quality == ConnectionQuality.POOR) {
            session.updateVideoConfig(
                currentConfig.copy(
                    width = profile.width,
                    height = profile.height,
                    bitrate = profile.bitrate,
                    frameRate = profile.frameRate
                )
            )
        }
    }

    data class BitrateProfile(
        val bitrate: Int,
        val frameRate: Int,
        val width: Int,
        val height: Int
    )
}
```

## 高级配置

### 1. 自定义编码器配置

```kotlin
val session = createStreamSession {
    video {
        width = 1920
        height = 1080
        frameRate = 60
        bitrate = 8_000_000

        // 高级编码器设置
        codec = VideoCodec.H264
        profile = VideoProfile.HIGH
        level = VideoLevel.LEVEL_4_1
        bitrateMode = BitrateMode.CBR
        keyFrameInterval = 1

        // 硬件加速
        enableHardwareAcceleration = true
        preferredEncoder = "OMX.qcom.video.encoder.avc" // 指定编码器

        // 质量控制
        qualityPreset = QualityPreset.HIGH_QUALITY
        enableBFrames = true
        maxBFrames = 2

        // 低延迟设置
        enableLowLatency = true
        tuning = EncoderTuning.ZERO_LATENCY
    }

    audio {
        sampleRate = 48000
        bitrate = 320_000
        channels = 2

        // 高级音频设置
        codec = AudioCodec.AAC
        profile = AudioProfile.LC
        enableVBR = true

        // 音频处理
        enableAEC = true
        enableAGC = true
        enableNoiseReduction = true
        enableHighPassFilter = true

        // 低延迟音频
        bufferSize = 1024
        enableLowLatency = true
    }

    addRtmp("rtmp://live.example.com/live/stream")
}
```

### 2. 性能优化配置

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
    }

    addRtmp("rtmp://live.example.com/live/stream")

    advanced {
        // 性能优化
        enableGpuAcceleration = true
        bufferStrategy = BufferStrategy.ADAPTIVE
        threadPoolSize = 4

        // 内存管理
        enableMemoryPool = true
        maxBufferSize = 50 * 1024 * 1024 // 50MB

        // 网络优化
        enableTcpNoDelay = true
        socketBufferSize = 64 * 1024

        // 编码优化
        enableParallelEncoding = true
        encoderThreads = 2

        // 预处理优化
        enablePreprocessing = true
        preprocessingThreads = 1
    }
}
```

### 3. 调试和诊断配置

```kotlin
val session = createStreamSession {
    // 基础配置...

    advanced {
        // 调试选项
        enableDebugMode = BuildConfig.DEBUG
        enableVerboseLogging = true
        enablePerformanceMetrics = true

        // 统计收集
        enableDetailedStats = true
        statsInterval = 1.seconds

        // 诊断功能
        enableNetworkDiagnostics = true
        enableEncoderDiagnostics = true
        enableFrameAnalysis = true

        // 错误报告
        enableErrorReporting = true
        errorReportingLevel = ErrorLevel.WARNING
    }
}

// 启用详细日志
if (BuildConfig.DEBUG) {
    session.setLogLevel(LogLevel.VERBOSE)
    session.enableFrameLogging(true)
}
```

## 错误处理

### 1. 全面的错误处理

```kotlin
class RobustStreamingActivity : AppCompatActivity() {
    private lateinit var session: UnifiedStreamSession
    private var isRecovering = false

    private fun setupErrorHandling() {
        lifecycleScope.launch {
            session.state.collect { state ->
                when (state) {
                    is SessionState.ERROR -> handleSessionError(state.error)
                    SessionState.STREAMING -> {
                        isRecovering = false
                        hideErrorUI()
                    }
                    else -> { /* 其他状态 */ }
                }
            }
        }

        // 监听传输错误
        lifecycleScope.launch {
            session.observeTransportErrors().collect { (transportId, error) ->
                handleTransportError(transportId, error)
            }
        }
    }

    private suspend fun handleSessionError(error: StreamError) {
        when (error) {
            is TransportError.ConnectionFailed -> {
                showError("连接失败: ${error.message}")
                if (error.recoverable && !isRecovering) {
                    attemptRecovery(error)
                }
            }
            is TransportError.AuthenticationFailed -> {
                showError("认证失败，请检查推流密钥")
                // 不可恢复错误，需要用户干预
            }
            is EncodingError.HardwareEncoderFailed -> {
                showError("硬件编码器失败，切换到软件编码")
                switchToSoftwareEncoder()
            }
            is NetworkError -> {
                showError("网络错误: ${error.message}")
                if (error.recoverable) {
                    waitAndRetry()
                }
            }
        }
    }

    private suspend fun handleTransportError(transportId: TransportId, error: TransportError) {
        val transport = session.getTransport(transportId)
        Log.w("Streaming", "Transport ${transport?.protocol} error: ${error.message}")

        when (error) {
            is TransportError.ConnectionFailed -> {
                // 尝试重连
                retryTransportConnection(transportId)
            }
            is TransportError.NetworkError -> {
                // 切换到备用传输
                switchToFallbackTransport(transportId)
            }
        }
    }

    private suspend fun attemptRecovery(error: StreamError) {
        if (isRecovering) return
        isRecovering = true

        showRecoveryUI("正在尝试恢复连接...")

        try {
            // 等待网络恢复
            delay(2000)

            // 重新连接
            session.reconnect()

            showRecoveryUI("恢复成功")
            delay(1000)
            hideErrorUI()
        } catch (e: Exception) {
            showError("恢复失败: ${e.message}")
        } finally {
            isRecovering = false
        }
    }

    private suspend fun switchToSoftwareEncoder() {
        val currentConfig = session.getVideoConfig()
        session.updateVideoConfig(
            currentConfig.copy(
                enableHardwareAcceleration = false,
                preferredEncoder = "software"
            )
        )
        session.restart()
    }

    private suspend fun retryTransportConnection(transportId: TransportId) {
        repeat(3) { attempt ->
            try {
                session.reconnectTransport(transportId)
                return // 成功
            } catch (e: Exception) {
                if (attempt < 2) {
                    delay(2000 * (attempt + 1)) // 递增延迟
                }
            }
        }
        // 重试失败，移除此传输
        session.removeTransport(transportId)
    }
}
```

### 2. 网络中断恢复

```kotlin
class NetworkRecoveryManager(private val session: UnifiedStreamSession) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    fun startMonitoring() {
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                // 网络恢复
                GlobalScope.launch {
                    handleNetworkAvailable()
                }
            }

            override fun onLost(network: Network) {
                // 网络丢失
                GlobalScope.launch {
                    handleNetworkLost()
                }
            }
        }

        connectivityManager?.registerDefaultNetworkCallback(networkCallback)
    }

    private suspend fun handleNetworkAvailable() {
        Log.i("NetworkRecovery", "Network available, attempting to reconnect")

        try {
            // 等待网络稳定
            delay(3000)

            // 检查会话状态
            if (session.state.value is SessionState.ERROR) {
                session.reconnect()
            }
        } catch (e: Exception) {
            Log.e("NetworkRecovery", "Failed to reconnect: ${e.message}")
        }
    }

    private suspend fun handleNetworkLost() {
        Log.w("NetworkRecovery", "Network lost")
        // 可以选择暂停推流或切换到离线模式
    }
}
```

## 性能监控

### 1. 实时性能监控

```kotlin
class PerformanceMonitor(private val session: UnifiedStreamSession) {
    private val metricsCollector = MetricsCollector()

    fun startMonitoring() {
        // 监控推流统计
        GlobalScope.launch {
            session.stats.collect { stats ->
                updatePerformanceUI(stats)
                checkPerformanceThresholds(stats)
            }
        }

        // 监控系统资源
        GlobalScope.launch {
            while (true) {
                val systemMetrics = collectSystemMetrics()
                metricsCollector.record(systemMetrics)
                delay(1000)
            }
        }
    }

    private fun updatePerformanceUI(stats: StreamStats) {
        findViewById<TextView>(R.id.tv_bitrate).text =
            "码率: ${stats.averageBitrate / 1000} kbps"

        findViewById<TextView>(R.id.tv_fps).text =
            "帧率: ${stats.currentFps} fps"

        findViewById<TextView>(R.id.tv_dropped_frames).text =
            "丢帧: ${stats.framesDropped}"

        findViewById<ProgressBar>(R.id.pb_cpu_usage).progress =
            stats.cpuUsage.toInt()

        findViewById<ProgressBar>(R.id.pb_memory_usage).progress =
            stats.memoryUsage.toInt()
    }

    private fun checkPerformanceThresholds(stats: StreamStats) {
        // 检查丢帧率
        val dropRate = stats.framesDropped.toFloat() / stats.framesSent
        if (dropRate > 0.05) { // 5%
            Log.w("Performance", "High drop rate: ${dropRate * 100}%")
            // 可以自动降低码率或分辨率
        }

        // 检查CPU使用率
        if (stats.cpuUsage > 80) {
            Log.w("Performance", "High CPU usage: ${stats.cpuUsage}%")
            // 可以降低编码复杂度
        }

        // 检查内存使用
        if (stats.memoryUsage > 500 * 1024 * 1024) { // 500MB
            Log.w("Performance", "High memory usage: ${stats.memoryUsage / 1024 / 1024}MB")
            // 可以触发内存清理
        }
    }

    private fun collectSystemMetrics(): SystemMetrics {
        val runtime = Runtime.getRuntime()
        val activityManager = context.getSystemService<ActivityManager>()

        return SystemMetrics(
            cpuUsage = getCpuUsage(),
            memoryUsage = runtime.totalMemory() - runtime.freeMemory(),
            availableMemory = activityManager?.memoryInfo?.availMem ?: 0L,
            batteryLevel = getBatteryLevel(),
            temperature = getDeviceTemperature()
        )
    }
}
```

### 2. 性能分析和优化

```kotlin
class PerformanceAnalyzer {
    private val frameTimeHistory = mutableListOf<Long>()
    private val bitrateHistory = mutableListOf<Int>()

    fun analyzePerformance(session: UnifiedStreamSession) {
        GlobalScope.launch {
            session.observeFrameMetrics().collect { metrics ->
                analyzeFramePerformance(metrics)
            }
        }

        GlobalScope.launch {
            session.observeBitrateMetrics().collect { bitrate ->
                analyzeBitrateStability(bitrate)
            }
        }
    }

    private fun analyzeFramePerformance(metrics: FrameMetrics) {
        frameTimeHistory.add(metrics.processingTime)

        // 保持最近100帧的历史
        if (frameTimeHistory.size > 100) {
            frameTimeHistory.removeAt(0)
        }

        // 分析帧时间稳定性
        val avgFrameTime = frameTimeHistory.average()
        val variance = frameTimeHistory.map { (it - avgFrameTime).pow(2) }.average()
        val stdDev = sqrt(variance)

        if (stdDev > avgFrameTime * 0.3) { // 30%的变异系数
            Log.w("Performance", "Unstable frame timing detected")
            // 可以建议调整编码参数
        }
    }

    private fun analyzeBitrateStability(bitrate: Int) {
        bitrateHistory.add(bitrate)

        if (bitrateHistory.size > 60) { // 保持60秒历史
            bitrateHistory.removeAt(0)
        }

        // 计算码率波动
        val avgBitrate = bitrateHistory.average()
        val maxDeviation = bitrateHistory.maxOf { abs(it - avgBitrate) }

        if (maxDeviation > avgBitrate * 0.5) { // 50%波动
            Log.w("Performance", "High bitrate instability detected")
            // 可以建议启用更稳定的码率控制
        }
    }
}
```

## 实际应用场景

### 1. 游戏直播应用

```kotlin
class GameStreamingActivity : AppCompatActivity() {
    private lateinit var session: UnifiedStreamSession

    private fun setupGameStreaming() {
        session = createStreamSession {
            // 游戏直播优化配置
            video {
                width = 1920
                height = 1080
                frameRate = 60 // 高帧率
                bitrate = 6_000_000
                keyFrameInterval = 1 // 更频繁的关键帧
                enableLowLatency = true
                codec = VideoCodec.H264
                profile = VideoProfile.HIGH
                tuning = EncoderTuning.GAMING
            }

            audio {
                sampleRate = 48000
                bitrate = 192_000
                enableAEC = false // 游戏音频不需要回声消除
                enableNoiseReduction = true
                enableLowLatency = true
            }

            // 多平台推流
            addRtmp("rtmp://live.twitch.tv/live/$TWITCH_KEY")
            addRtmp("rtmp://a.rtmp.youtube.com/live2/$YOUTUBE_KEY")

            advanced {
                enableSimultaneousPush = true
                enableGpuAcceleration = true
                bufferStrategy = BufferStrategy.LOW_LATENCY
            }
        }

        // 游戏性能监控
        setupGamePerformanceMonitoring()
    }

    private fun setupGamePerformanceMonitoring() {
        lifecycleScope.launch {
            session.stats.collect { stats ->
                // 如果帧率下降，自动调整编码参数
                if (stats.currentFps < 50) {
                    val currentConfig = session.getVideoConfig()
                    session.updateVideoConfig(
                        currentConfig.copy(
                            frameRate = 30,
                            bitrate = (currentConfig.bitrate * 0.8).toInt()
                        )
                    )
                }
            }
        }
    }
}
```

### 2. 教育直播应用

```kotlin
class EducationStreamingActivity : AppCompatActivity() {
    private lateinit var session: UnifiedStreamSession

    private fun setupEducationStreaming() {
        session = createStreamSession {
            // 教育直播配置（稳定性优先）
            video {
                width = 1280
                height = 720
                frameRate = 25 // 适中帧率
                bitrate = 1_500_000
                keyFrameInterval = 3
                enableAdaptiveBitrate = true
                bitrateMode = BitrateMode.VBR
            }

            audio {
                sampleRate = 44100
                bitrate = 128_000
                enableAEC = true // 重要：消除回声
                enableAGC = true // 自动增益控制
                enableNoiseReduction = true
            }

            // 主推流（高质量）
            addWebRtc("wss://education.signal.com", "classroom123") {
                priority = 0
                audioCodec = AudioCodec.OPUS
                videoCodec = VideoCodec.H264
            }

            // 备用推流（稳定性）
            addRtmp("rtmp://education.live.com/live/class123") {
                priority = 1
                retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 5)
            }

            advanced {
                primaryTransport = TransportProtocol.WEBRTC
                fallbackEnabled = true
                autoSwitchThreshold = ConnectionQuality.FAIR
            }
        }

        // 教育场景特殊功能
        setupEducationFeatures()
    }

    private fun setupEducationFeatures() {
        // 屏幕共享切换
        findViewById<Button>(R.id.btn_share_screen).setOnClickListener {
            toggleScreenShare()
        }

        // 白板模式
        findViewById<Button>(R.id.btn_whiteboard).setOnClickListener {
            enableWhiteboardMode()
        }

        // 录制功能
        findViewById<Button>(R.id.btn_record).setOnClickListener {
            toggleRecording()
        }
    }

    private fun toggleScreenShare() {
        lifecycleScope.launch {
            val isScreenSharing = session.isScreenSharing()
            if (isScreenSharing) {
                session.stopScreenShare()
                session.startCameraCapture()
            } else {
                session.stopCameraCapture()
                session.startScreenShare()
            }
        }
    }

    private fun enableWhiteboardMode() {
        // 切换到白板优化模式
        val whiteboardConfig = session.getVideoConfig().copy(
            frameRate = 15, // 白板不需要高帧率
            bitrate = 800_000, // 降低码率
            tuning = EncoderTuning.SCREEN_CONTENT
        )
        session.updateVideoConfig(whiteboardConfig)
    }
}
```

### 3. 户外直播应用

```kotlin
class OutdoorStreamingActivity : AppCompatActivity() {
    private lateinit var session: UnifiedStreamSession
    private lateinit var networkMonitor: NetworkQualityMonitor

    private fun setupOutdoorStreaming() {
        session = createStreamSession {
            // 户外直播配置（网络适应性优先）
            video {
                width = 1280
                height = 720
                frameRate = 30
                bitrate = 2_000_000
                enableAdaptiveBitrate = true
                bitrateMode = BitrateMode.VBR
                minBitrate = 300_000 // 设置最低码率
                maxBitrate = 4_000_000
            }

            audio {
                sampleRate = 44100
                bitrate = 128_000
                enableAEC = true
                enableNoiseReduction = true
                enableWindNoiseReduction = true // 户外特有
            }

            camera {
                facing = CameraFacing.BACK
                stabilization = true // 重要：防抖
                autoFocus = true
                exposureMode = ExposureMode.AUTO
            }

            // 多级备用方案
            addWebRtc("wss://signal.example.com", "outdoor123") {
                priority = 0
                maxBitrate = 4_000_000
            }

            addRtmp("rtmp://live.example.com/live/outdoor") {
                priority = 1
                enableLowLatency = false
                retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 10)
            }

            // SRT作为最后备用（如果支持）
            addSrt("srt://backup.example.com:9999") {
                priority = 2
                latency = 2000 // 2秒延迟换取稳定性
            }

            advanced {
                enableSimultaneousPush = false // 节省流量
                primaryTransport = TransportProtocol.WEBRTC
                fallbackEnabled = true
                autoSwitchThreshold = ConnectionQuality.POOR
                enableAggressiveRetry = true
            }
        }

        setupOutdoorOptimizations()
    }

    private fun setupOutdoorOptimizations() {
        // 网络质量监控
        networkMonitor = NetworkQualityMonitor(this)
        networkMonitor.startMonitoring { quality, signalStrength ->
            adjustForNetworkConditions(quality, signalStrength)
        }

        // GPS位置监控（某些场景需要）
        if (hasLocationPermission()) {
            startLocationUpdates()
        }

        // 电池优化
        setupBatteryOptimization()
    }

    private suspend fun adjustForNetworkConditions(
        quality: ConnectionQuality,
        signalStrength: Int
    ) {
        val currentConfig = session.getVideoConfig()

        when {
            signalStrength < -80 -> { // 信号很弱
                session.updateVideoConfig(
                    currentConfig.copy(
                        width = 640,
                        height = 360,
                        frameRate = 15,
                        bitrate = 300_000
                    )
                )
            }
            quality == ConnectionQuality.POOR -> {
                session.updateVideoConfig(
                    currentConfig.copy(
                        frameRate = 20,
                        bitrate = (currentConfig.bitrate * 0.6).toInt()
                    )
                )
            }
            quality == ConnectionQuality.EXCELLENT -> {
                session.updateVideoConfig(
                    currentConfig.copy(
                        width = 1280,
                        height = 720,
                        frameRate = 30,
                        bitrate = 2_000_000
                    )
                )
            }
        }
    }

    private fun setupBatteryOptimization() {
        val batteryManager = getSystemService<BatteryManager>()

        lifecycleScope.launch {
            while (true) {
                val batteryLevel = batteryManager?.getIntProperty(
                    BatteryManager.BATTERY_PROPERTY_CAPACITY
                ) ?: 100

                if (batteryLevel < 20) {
                    // 低电量模式
                    enableLowPowerMode()
                } else if (batteryLevel > 50) {
                    // 恢复正常模式
                    disableLowPowerMode()
                }

                delay(30000) // 每30秒检查一次
            }
        }
    }

    private suspend fun enableLowPowerMode() {
        val currentConfig = session.getVideoConfig()
        session.updateVideoConfig(
            currentConfig.copy(
                frameRate = 20,
                bitrate = (currentConfig.bitrate * 0.7).toInt(),
                enableHardwareAcceleration = true // 硬件编码更省电
            )
        )
    }
}
```

这些示例展示了统一推流SDK在各种实际场景中的应用，从基础功能到高级特性，从性能优化到错误处理，为开发者提供了全面的参考。