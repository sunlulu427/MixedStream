package com.astra.avpush.unified

import java.time.Duration
import java.util.UUID

/**
 * 传输ID类型别名
 */
typealias TransportId = String

/**
 * 传输协议枚举
 */
enum class TransportProtocol(val displayName: String, val defaultLatency: Int) {
    RTMP("RTMP", 3000),
    SRT("SRT", 500),
    RTSP("RTSP", 1000)
}

/**
 * 传输配置基类
 */
sealed class TransportConfig {
    abstract val id: TransportId
    abstract val priority: Int
    abstract val enabled: Boolean
    abstract val protocol: TransportProtocol

    companion object {
        fun generateId(): TransportId = UUID.randomUUID().toString()
    }
}

/**
 * RTMP传输配置
 */
data class RtmpConfig(
    override val id: TransportId = generateId(),
    override val priority: Int = 1,
    override val enabled: Boolean = true,
    val pushUrl: String,
    val connectTimeout: Duration = Duration.ofSeconds(10),
    val retryPolicy: RetryPolicy = RetryPolicy.exponentialBackoff(),
    val chunkSize: Int = 4096,
    val enableLowLatency: Boolean = false,
    val enableTcpNoDelay: Boolean = true
) : TransportConfig() {
    override val protocol = TransportProtocol.RTMP
}

/**
 * SRT传输配置
 */
data class SrtConfig(
    override val id: TransportId = generateId(),
    override val priority: Int = 2,
    override val enabled: Boolean = true,
    val serverUrl: String,
    val latency: Duration = Duration.ofMillis(500),
    val encryption: SrtEncryption = SrtEncryption.NONE,
    val streamId: String? = null
) : TransportConfig() {
    override val protocol = TransportProtocol.SRT
}

/**
 * 音频编解码器
 */
enum class AudioCodec {
    AAC,
    OPUS,
    G711,
    G722
}

/**
 * 视频编解码器
 */
enum class VideoCodec {
    H264,
    H265,
    VP8,
    VP9,
    AV1
}

/**
 * SRT加密方式
 */
enum class SrtEncryption {
    NONE,
    AES_128,
    AES_192,
    AES_256
}

/**
 * 重试策略
 */
data class RetryPolicy(
    val maxRetries: Int = 3,
    val baseDelay: Duration = Duration.ofSeconds(1),
    val maxDelay: Duration = Duration.ofSeconds(30),
    val backoffMultiplier: Double = 2.0,
    val jitter: Boolean = true
) {
    companion object {
        fun exponentialBackoff(
            maxRetries: Int = 3,
            baseDelay: Duration = Duration.ofSeconds(1),
            maxDelay: Duration = Duration.ofSeconds(30)
        ): RetryPolicy = RetryPolicy(
            maxRetries = maxRetries,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            backoffMultiplier = 2.0,
            jitter = true
        )

        fun fixedDelay(
            maxRetries: Int = 3,
            delay: Duration = Duration.ofSeconds(5)
        ): RetryPolicy = RetryPolicy(
            maxRetries = maxRetries,
            baseDelay = delay,
            maxDelay = delay,
            backoffMultiplier = 1.0,
            jitter = false
        )

        fun noRetry(): RetryPolicy = RetryPolicy(
            maxRetries = 0,
            baseDelay = Duration.ZERO,
            maxDelay = Duration.ZERO,
            backoffMultiplier = 1.0,
            jitter = false
        )
    }

    fun getDelay(attempt: Int): Duration {
        if (maxRetries == 0) return Duration.ZERO

        val delay = minOf(
            baseDelay.toMillis() * Math.pow(backoffMultiplier, attempt.toDouble()),
            maxDelay.toMillis().toDouble()
        ).toLong()

        return if (jitter) {
            Duration.ofMillis((delay * (0.5 + Math.random() * 0.5)).toLong())
        } else {
            Duration.ofMillis(delay)
        }
    }
}