package com.astra.avpush.unified

import java.time.Duration

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