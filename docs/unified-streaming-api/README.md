# 统一推流SDK接口设计方案

## 概述

本文档描述了AVRtmpPushSDK的统一推流接口设计方案，该方案旨在提供一套兼容RTC和RTMP的统一API，支持多协议并发推流、智能切换和自适应码率等高级功能。

## 文档结构

```
docs/unified-streaming-api/
├── README.md                    # 本文档 - 方案概述
├── DESIGN.md                    # 详细设计文档
├── API_REFERENCE.md             # API参考文档
├── MIGRATION_GUIDE.md           # 迁移指南
├── EXAMPLES.md                  # 使用示例
├── IMPLEMENTATION_PLAN.md       # 实施计划
└── ARCHITECTURE.md              # 架构设计
```

## 设计目标

1. **协议无关性** - 业务层无需关心底层传输协议差异
2. **多协议支持** - 同时支持RTMP、WebRTC、SRT等协议
3. **同时推流** - 支持多协议并发推流和智能切换
4. **类型安全** - 编译时检查，减少运行时错误
5. **响应式** - 基于Kotlin Flow的状态管理
6. **向后兼容** - 保持现有API的兼容性
7. **可扩展性** - 易于添加新的传输协议

## 核心特性

### 🎯 统一接口设计

```kotlin
val session = createStreamSession {
    audio {
        sampleRate = 44100
        bitrate = 128_000
        enableAEC = true
    }
    video {
        width = 1280
        height = 720
        frameRate = 30
        bitrate = 2_000_000
    }

    // 同时推流到多个协议
    addRtmp("rtmp://live.example.com/live/stream")
    addWebRtc("wss://signal.example.com", "room123")

    advanced {
        enableSimultaneousPush = true
        primaryTransport = TransportProtocol.WEBRTC
        fallbackEnabled = true
    }
}
```

### 🔄 响应式状态管理

```kotlin
// 监听连接质量并自动切换协议
session.observeConnectionQuality().collect { quality ->
    when (quality) {
        ConnectionQuality.POOR -> session.switchPrimaryTransport(TransportProtocol.RTMP)
        ConnectionQuality.GOOD -> session.switchPrimaryTransport(TransportProtocol.WEBRTC)
    }
}
```

### 🛡️ 类型安全配置

使用Kotlin密封类和强类型确保配置的正确性：

```kotlin
sealed class TransportConfig {
    data class RtmpConfig(val pushUrl: String) : TransportConfig()
    data class WebRtcConfig(val signalingUrl: String, val roomId: String) : TransportConfig()
}
```

## 架构概览

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

## 主要优势

### ✅ 向后兼容
- 现有`LiveStreamSession`接口完全保持
- 提供适配器模式实现无缝迁移
- 支持渐进式重构

### ✅ 多协议支持
- RTMP：基于现有实现，稳定可靠
- WebRTC：低延迟实时通信
- SRT：安全可靠传输（规划中）

### ✅ 智能特性
- 自适应码率调节
- 网络质量监测
- 自动故障转移
- 连接重试机制

### ✅ 开发体验
- Kotlin DSL风格配置
- 类型安全的API设计
- 响应式状态管理
- 丰富的错误处理

## 快速开始

### 1. 基础推流

```kotlin
val session = createStreamSession {
    audio { sampleRate = 44100; bitrate = 128_000 }
    video { width = 1280; height = 720; frameRate = 30 }
    camera { facing = CameraFacing.BACK }
    addRtmp("rtmp://your-server.com/live/stream")
}

lifecycleScope.launch {
    session.prepare(this@Activity, surfaceProvider)
    session.start()
}
```

### 2. 多协议推流

```kotlin
val session = createStreamSession {
    // ... 基础配置
    addRtmp("rtmp://live.example.com/live/stream")
    addWebRtc("wss://signal.example.com", "room123")

    advanced {
        enableSimultaneousPush = true
        primaryTransport = TransportProtocol.WEBRTC
        fallbackTransports = listOf(TransportProtocol.RTMP)
    }
}
```

### 3. 状态监听

```kotlin
// 监听会话状态
session.state.collect { state ->
    when (state) {
        SessionState.STREAMING -> showStreamingUI()
        SessionState.ERROR -> showErrorDialog()
    }
}

// 监听传输统计
session.getAllTransportStats().collect { stats ->
    updateStatsUI(stats)
}
```

## 实施计划

| 阶段 | 时间 | 内容 |
|------|------|------|
| 阶段1 | 2-3周 | 核心接口实现和RTMP适配 |
| 阶段2 | 3-4周 | WebRTC集成和信令处理 |
| 阶段3 | 2-3周 | 统一会话管理和多协议支持 |
| 阶段4 | 2-3周 | 高级功能和性能优化 |

## 文档导航

- [详细设计文档](./DESIGN.md) - 深入了解技术设计细节
- [API参考文档](./API_REFERENCE.md) - 完整的API说明
- [迁移指南](./MIGRATION_GUIDE.md) - 从现有API迁移的步骤
- [使用示例](./EXAMPLES.md) - 丰富的使用场景示例
- [架构设计](./ARCHITECTURE.md) - 系统架构和设计原则

## 贡献

欢迎提交Issue和Pull Request来改进这个设计方案。请确保：

1. 遵循现有的代码风格和架构原则
2. 添加适当的测试用例
3. 更新相关文档

## 许可证

本项目采用与主项目相同的许可证。