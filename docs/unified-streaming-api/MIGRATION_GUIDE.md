# 迁移指南

## 概述

本指南帮助现有AVRtmpPushSDK用户从当前API迁移到新的统一推流接口。新接口保持向后兼容，支持渐进式迁移。

> **提示**：Runtime 已切换至 `NativeSenderBridge`。文中的 `RtmpStreamSession` 示例代表旧版接口，在新架构中应替换为 `NativeSender` 句柄。

## 迁移策略

### 策略1: 适配器模式（推荐）
保持现有代码不变，通过适配器使用新功能。

### 策略2: 渐进式重构
逐步将代码迁移到新API，享受更好的类型安全和功能。

### 策略3: 完全重写
对于新项目或大规模重构，直接使用新API。

## 当前API映射关系

### 基础推流接口

#### 旧API
```kotlin
// 旧的推流方式
val live = AVLiveView(context)
live.setAudioConfigure(audioConfig)
live.setVideoConfigure(videoConfig)
live.setCameraConfigure(cameraConfig)

val sender = RtmpStreamSession()
sender.connect("rtmp://live.example.com/live/stream")

live.startPreview()
live.startLive()
```

#### 新API（直接迁移）
```kotlin
// 新的统一接口
val session = createStreamSession {
    audio {
        sampleRate = audioConfig.sampleRate
        bitrate = audioConfig.bitrate
        channels = audioConfig.channels
    }
    video {
        width = videoConfig.width
        height = videoConfig.height
        frameRate = videoConfig.frameRate
        bitrate = videoConfig.bitrate
    }
    camera {
        facing = cameraConfig.facing
        autoFocus = cameraConfig.autoFocus
    }
    addRtmp("rtmp://live.example.com/live/stream")
}

lifecycleScope.launch {
    session.prepare(this@Activity, surfaceProvider)
    session.start()
}
```

#### 适配器方式（零代码修改）
```kotlin
// 使用适配器，现有代码无需修改
val live = AVLiveView(context)
// 在后台自动使用新的统一接口
live.enableUnifiedInterface(true)

// 现有代码保持不变
live.setAudioConfigure(audioConfig)
live.setVideoConfigure(videoConfig)
live.setCameraConfigure(cameraConfig)

val sender = RtmpStreamSession()
sender.connect("rtmp://live.example.com/live/stream")

live.startPreview()
live.startLive()
```

## 详细迁移步骤

### 步骤1: 添加依赖

在`build.gradle`中添加新的统一接口依赖：

```gradle
dependencies {
    implementation 'com.astrastream:avpush-unified:1.0.0'
    // 保持现有依赖以确保兼容性
    implementation 'com.astrastream:avpush:0.9.x'
}
```

### 步骤2: 配置对象迁移

#### AudioConfigure → AudioConfig

```kotlin
// 旧配置
val audioConfig = AudioConfigure().apply {
    sampleRate = 44100
    bitrate = 128000
    channels = 2
    aec = true
    agc = true
}

// 新配置
val audioConfig = AudioConfig(
    sampleRate = 44100,
    bitrate = 128_000,
    channels = 2,
    enableAEC = true,
    enableAGC = true,
    enableNoiseReduction = true
)

// 或者使用DSL
val session = createStreamSession {
    audio {
        sampleRate = 44100
        bitrate = 128_000
        channels = 2
        enableAEC = true
        enableAGC = true
        enableNoiseReduction = true
    }
}
```

#### VideoConfigure → VideoConfig

```kotlin
// 旧配置
val videoConfig = VideoConfigure().apply {
    width = 1280
    height = 720
    fps = 30
    bitrate = 2000000
    ifi = 2
}

// 新配置
val videoConfig = VideoConfig(
    width = 1280,
    height = 720,
    frameRate = 30,
    bitrate = 2_000_000,
    keyFrameInterval = 2,
    enableAdaptiveBitrate = true
)

// 或者使用DSL
val session = createStreamSession {
    video {
        width = 1280
        height = 720
        frameRate = 30
        bitrate = 2_000_000
        keyFrameInterval = 2
        enableAdaptiveBitrate = true
    }
}
```

#### CameraConfigure → CameraConfig

```kotlin
// 旧配置
val cameraConfig = CameraConfigure().apply {
    facing = CameraConfigure.Facing.BACK
    autoFocus = true
}

// 新配置
val cameraConfig = CameraConfig(
    facing = CameraFacing.BACK,
    autoFocus = true,
    stabilization = true,
    exposureMode = ExposureMode.AUTO
)

// 或者使用DSL
val session = createStreamSession {
    camera {
        facing = CameraFacing.BACK
        autoFocus = true
        stabilization = true
    }
}
```

### 步骤3: 生命周期管理迁移

#### 旧的生命周期管理
```kotlin
class LiveActivity : AppCompatActivity() {
    private lateinit var live: AVLiveView
    private lateinit var sender: RtmpStreamSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        live = findViewById(R.id.live_view)
        sender = RtmpStreamSession()

        // 配置
        live.setAudioConfigure(audioConfig)
        live.setVideoConfigure(videoConfig)
        live.setCameraConfigure(cameraConfig)

        // 开始预览
        live.startPreview()
    }

    private fun startStreaming() {
        sender.connect(rtmpUrl)
        live.startLive()
    }

    private fun stopStreaming() {
        live.stopLive()
        sender.close()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        live.release()
    }
}
```

#### 新的生命周期管理
```kotlin
class LiveActivity : AppCompatActivity() {
    private lateinit var session: UnifiedStreamSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        session = createStreamSession {
            audio { /* 配置 */ }
            video { /* 配置 */ }
            camera { /* 配置 */ }
            addRtmp(rtmpUrl)
        }

        // 监听状态变化
        lifecycleScope.launch {
            session.state.collect { state ->
                when (state) {
                    SessionState.STREAMING -> showStreamingUI()
                    SessionState.ERROR -> showError(state.error)
                    else -> hideStreamingUI()
                }
            }
        }
    }

    private suspend fun startStreaming() {
        session.prepare(this, surfaceProvider)
        session.start()
    }

    private suspend fun stopStreaming() {
        session.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.launch {
            session.release()
        }
    }
}
```

### 步骤4: 事件监听迁移

#### 旧的事件监听
```kotlin
live.setOnLiveStateListener(object : OnLiveStateListener {
    override fun onLiveStart() {
        // 推流开始
    }

    override fun onLiveStop() {
        // 推流停止
    }

    override fun onLiveError(error: String) {
        // 推流错误
    }
})

sender.setOnSenderListener(object : OnSenderListener {
    override fun onConnected() {
        // 连接成功
    }

    override fun onDisconnected() {
        // 连接断开
    }

    override fun onError(error: String) {
        // 连接错误
    }
})
```

#### 新的事件监听
```kotlin
// 使用Flow监听状态变化
lifecycleScope.launch {
    session.state.collect { state ->
        when (state) {
            SessionState.STREAMING -> onLiveStart()
            SessionState.IDLE -> onLiveStop()
            is SessionState.ERROR -> onLiveError(state.error.message)
        }
    }
}

// 监听传输状态
lifecycleScope.launch {
    session.observeTransportState(transportId).collect { state ->
        when (state) {
            TransportState.CONNECTED -> onConnected()
            TransportState.DISCONNECTED -> onDisconnected()
            is TransportState.ERROR -> onError(state.error.message)
        }
    }
}

// 监听连接质量
lifecycleScope.launch {
    session.observeConnectionQuality().collect { quality ->
        updateQualityIndicator(quality)
    }
}
```

## 功能对照表

| 旧功能 | 新功能 | 说明 |
|--------|--------|------|
| `live.startLive()` | `session.start()` | 开始推流 |
| `live.stopLive()` | `session.stop()` | 停止推流 |
| `live.setVideoBps(bps)` | `session.updateVideoConfig(config.copy(bitrate = bps))` | 动态调整码率 |
| `live.switchCamera()` | `session.updateCameraConfig(config.copy(facing = newFacing))` | 切换摄像头 |
| `sender.connect(url)` | `session.addTransport(RtmpConfig(pushUrl = url))` | 添加RTMP传输 |
| `sender.close()` | `session.removeTransport(transportId)` | 移除传输 |
| `live.setWatermark(watermark)` | `session.setWatermark(watermark)` | 设置水印 |

## 新功能示例

### 多协议推流
```kotlin
val session = createStreamSession {
    // 基础配置...

    // 同时推流到RTMP和WebRTC
    addRtmp("rtmp://live.example.com/live/stream")
    addWebRtc("wss://signal.example.com", "room123")

    advanced {
        enableSimultaneousPush = true
        primaryTransport = TransportProtocol.WEBRTC
        fallbackEnabled = true
    }
}
```

### 智能切换
```kotlin
// 监听连接质量并自动切换
lifecycleScope.launch {
    session.observeConnectionQuality().collect { quality ->
        when (quality) {
            ConnectionQuality.POOR -> {
                // 切换到RTMP作为备用
                session.switchPrimaryTransport(rtmpTransportId)
            }
            ConnectionQuality.GOOD -> {
                // 切换回WebRTC
                session.switchPrimaryTransport(webrtcTransportId)
            }
        }
    }
}
```

### 自适应码率
```kotlin
lifecycleScope.launch {
    session.observeConnectionQuality().collect { quality ->
        val currentConfig = session.getVideoConfig()
        val newBitrate = when (quality) {
            ConnectionQuality.EXCELLENT -> currentConfig.bitrate
            ConnectionQuality.GOOD -> (currentConfig.bitrate * 0.8).toInt()
            ConnectionQuality.FAIR -> (currentConfig.bitrate * 0.6).toInt()
            ConnectionQuality.POOR -> (currentConfig.bitrate * 0.4).toInt()
        }

        session.updateVideoConfig(
            currentConfig.copy(bitrate = newBitrate)
        )
    }
}
```

## 兼容性适配器

为了确保平滑迁移，我们提供了兼容性适配器：

### AVLiveViewAdapter

```kotlin
class AVLiveViewAdapter(context: Context) : AVLiveView(context) {
    private val unifiedSession: UnifiedStreamSession by lazy {
        createStreamSession { /* 从现有配置转换 */ }
    }

    override fun startLive() {
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
        val config = unifiedSession.getVideoConfig()
        unifiedSession.updateVideoConfig(config.copy(bitrate = bps))
    }

    // 其他方法的适配...
}
```

### RtmpStreamSessionAdapter

```kotlin
class RtmpStreamSessionAdapter : RtmpStreamSession {
    private var transportId: TransportId? = null
    private lateinit var session: UnifiedStreamSession

    override fun connect(url: String) {
        transportId = session.addTransport(RtmpConfig(pushUrl = url))
    }

    override fun close() {
        transportId?.let { session.removeTransport(it) }
    }

    // 其他方法的适配...
}
```

## 迁移检查清单

### 阶段1: 准备工作
- [ ] 备份现有代码
- [ ] 添加新依赖
- [ ] 运行现有测试确保基线正常

### 阶段2: 适配器集成
- [ ] 集成兼容性适配器
- [ ] 验证现有功能正常工作
- [ ] 添加新功能的测试用例

### 阶段3: 渐进式迁移
- [ ] 迁移配置对象
- [ ] 迁移生命周期管理
- [ ] 迁移事件监听
- [ ] 更新错误处理

### 阶段4: 新功能集成
- [ ] 添加多协议支持
- [ ] 实现智能切换
- [ ] 启用自适应码率
- [ ] 集成统计监控

### 阶段5: 清理和优化
- [ ] 移除旧代码
- [ ] 优化性能
- [ ] 更新文档
- [ ] 完善测试覆盖

## 常见问题

### Q: 迁移后性能会受到影响吗？
A: 不会。新接口在设计时充分考虑了性能，通过更好的内存管理和并发处理，实际上可能会有性能提升。

### Q: 现有的水印和滤镜功能还能用吗？
A: 完全兼容。新接口提供了更灵活的水印和滤镜API，同时保持向后兼容。

### Q: 如何处理现有的错误处理逻辑？
A: 新接口提供了更详细的错误分类和恢复机制。可以通过适配器保持现有逻辑，同时逐步迁移到新的错误处理方式。

### Q: 多协议推流会消耗更多资源吗？
A: 会有一定的资源消耗增加，但通过智能调度和资源共享，影响控制在可接受范围内。可以根据需要选择性启用多协议功能。

### Q: 迁移需要多长时间？
A: 使用适配器可以在1-2天内完成基础迁移，完整迁移到新API通常需要1-2周，具体时间取决于项目复杂度。

## 支持和帮助

如果在迁移过程中遇到问题，可以：

1. 查看[API参考文档](./API_REFERENCE.md)
2. 参考[使用示例](./EXAMPLES.md)
3. 提交Issue到GitHub仓库
4. 联系技术支持团队

迁移是一个渐进的过程，建议先在测试环境中验证，确保功能正常后再部署到生产环境。
