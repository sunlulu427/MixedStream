package com.astra.avpush.unified

import java.time.Duration

/**
 * 协议检测器
 *
 * 根据URL自动检测应该使用的传输协议，实现协议透明化
 */
object ProtocolDetector {

    /**
     * 根据URL自动检测并创建相应的传输配置
     */
    fun detectAndCreateConfig(url: String): TransportConfig {
        return when {
            isRtmpUrl(url) -> createRtmpConfig(url)
            isSrtUrl(url) -> createSrtConfig(url)
            else -> throw IllegalArgumentException("Unsupported streaming URL: $url")
        }
    }

    /**
     * 检测URL对应的协议类型
     */
    fun detectProtocol(url: String): TransportProtocol {
        return when {
            isRtmpUrl(url) -> TransportProtocol.RTMP
            isSrtUrl(url) -> TransportProtocol.SRT
            else -> throw IllegalArgumentException("Unsupported streaming URL: $url")
        }
    }

    /**
     * 检查是否为RTMP协议URL
     */
    private fun isRtmpUrl(url: String): Boolean {
        return url.startsWith("rtmp://", ignoreCase = true) ||
               url.startsWith("rtmps://", ignoreCase = true)
    }

    /**
     * 检查是否为SRT协议URL
     */
    private fun isSrtUrl(url: String): Boolean {
        return url.startsWith("srt://", ignoreCase = true)
    }

    /**
     * 创建RTMP传输配置
     */
    private fun createRtmpConfig(url: String): RtmpConfig {
        return RtmpConfig(
            pushUrl = url,
            connectTimeout = Duration.ofSeconds(10),
            retryPolicy = RetryPolicy.Companion.exponentialBackoff(maxRetries = 3),
            enableLowLatency = false,
            enableTcpNoDelay = true,
            chunkSize = 4096
        )
    }

    /**
     * 创建SRT传输配置
     */
    private fun createSrtConfig(url: String): SrtConfig {
        return SrtConfig(
            serverUrl = url,
            latency = Duration.ofMillis(200),
            encryption = SrtEncryption.NONE,
            streamId = null
        )
    }

    /**
     * 获取协议的显示名称
     */
    fun getProtocolDisplayName(protocol: TransportProtocol): String {
        return when (protocol) {
            TransportProtocol.RTMP -> "RTMP"
            TransportProtocol.SRT -> "SRT"
            TransportProtocol.RTSP -> "RTSP"
        }
    }

    /**
     * 检查协议是否支持低延迟
     */
    fun supportsLowLatency(protocol: TransportProtocol): Boolean {
        return when (protocol) {
            TransportProtocol.SRT -> true
            TransportProtocol.RTMP -> false
            TransportProtocol.RTSP -> true
        }
    }
}