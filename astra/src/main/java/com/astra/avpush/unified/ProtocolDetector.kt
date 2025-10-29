package com.astrastream.avpush.unified

import com.astrastream.avpush.unified.config.*
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
            isWebRtcUrl(url) -> createWebRtcConfig(url)
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
            isWebRtcUrl(url) -> TransportProtocol.WEBRTC
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
     * 检查是否为WebRTC协议URL
     */
    private fun isWebRtcUrl(url: String): Boolean {
        return url.startsWith("webrtc://", ignoreCase = true) ||
               url.startsWith("wss://", ignoreCase = true) ||
               url.startsWith("ws://", ignoreCase = true) ||
               url.contains("webrtc", ignoreCase = true)
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
            retryPolicy = RetryPolicy.exponentialBackoff(maxRetries = 3),
            enableLowLatency = false,
            enableTcpNoDelay = true,
            chunkSize = 4096
        )
    }

    /**
     * 创建WebRTC传输配置
     */
    private fun createWebRtcConfig(url: String): WebRtcConfig {
        // 解析WebRTC URL，提取信令服务器和房间信息
        val (signalingUrl, roomId) = parseWebRtcUrl(url)

        return WebRtcConfig(
            signalingUrl = signalingUrl,
            roomId = roomId,
            iceServers = listOf(
                IceServer("stun:stun.l.google.com:19302"),
                IceServer("stun:stun1.l.google.com:19302")
            ),
            audioCodec = AudioCodec.OPUS,
            videoCodec = VideoCodec.H264,
            enableDataChannel = false
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
     * 解析WebRTC URL
     * 支持格式：
     * - webrtc://signal.example.com/room123
     * - wss://signal.example.com?room=room123
     */
    private fun parseWebRtcUrl(url: String): Pair<String, String> {
        return when {
            url.startsWith("webrtc://") -> {
                val withoutProtocol = url.removePrefix("webrtc://")
                val parts = withoutProtocol.split("/", limit = 2)
                val signalingUrl = "wss://${parts[0]}"
                val roomId = if (parts.size > 1) parts[1] else "default"
                Pair(signalingUrl, roomId)
            }
            url.startsWith("wss://") || url.startsWith("ws://") -> {
                val roomId = extractRoomIdFromUrl(url) ?: "default"
                Pair(url, roomId)
            }
            else -> {
                throw IllegalArgumentException("Invalid WebRTC URL format: $url")
            }
        }
    }

    /**
     * 从URL中提取房间ID
     */
    private fun extractRoomIdFromUrl(url: String): String? {
        return try {
            val uri = java.net.URI(url)
            val query = uri.query ?: return null
            query.split("&")
                .map { it.split("=", limit = 2) }
                .find { it.size == 2 && it[0] == "room" }
                ?.get(1)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 获取协议的显示名称
     */
    fun getProtocolDisplayName(protocol: TransportProtocol): String {
        return when (protocol) {
            TransportProtocol.RTMP -> "RTMP"
            TransportProtocol.WEBRTC -> "WebRTC"
            TransportProtocol.SRT -> "SRT"
            TransportProtocol.RTSP -> "RTSP"
        }
    }

    /**
     * 检查协议是否支持低延迟
     */
    fun supportsLowLatency(protocol: TransportProtocol): Boolean {
        return when (protocol) {
            TransportProtocol.WEBRTC -> true
            TransportProtocol.SRT -> true
            TransportProtocol.RTMP -> false
            TransportProtocol.RTSP -> true
        }
    }
}