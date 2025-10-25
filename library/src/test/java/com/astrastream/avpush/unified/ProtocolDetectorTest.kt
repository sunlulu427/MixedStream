package com.astrastream.avpush.unified

import com.astrastream.avpush.unified.config.TransportProtocol
import org.junit.Test
import org.junit.Assert.*

/**
 * 协议检测器测试
 */
class ProtocolDetectorTest {

    @Test
    fun `should detect RTMP protocol`() {
        val protocol = ProtocolDetector.detectProtocol(TestConstants.RTMP_TEST_URL)
        assertEquals(TransportProtocol.RTMP, protocol)
    }

    @Test
    fun `should detect WebRTC protocol`() {
        val protocol = ProtocolDetector.detectProtocol(TestConstants.WEBRTC_TEST_URL)
        assertEquals(TransportProtocol.WEBRTC, protocol)
    }

    @Test
    fun `should detect SRT protocol`() {
        val protocol = ProtocolDetector.detectProtocol(TestConstants.SRT_TEST_URL)
        assertEquals(TransportProtocol.SRT, protocol)
    }

    @Test
    fun `should detect WebRTC from WSS URL`() {
        val protocol = ProtocolDetector.detectProtocol(TestConstants.WSS_TEST_URL)
        assertEquals(TransportProtocol.WEBRTC, protocol)
    }

    @Test
    fun `should create RTMP config`() {
        val config = ProtocolDetector.detectAndCreateConfig(TestConstants.RTMP_TEST_URL)
        assertEquals(TransportProtocol.RTMP, config.protocol)
    }

    @Test
    fun `should get protocol display name`() {
        assertEquals("RTMP", ProtocolDetector.getProtocolDisplayName(TransportProtocol.RTMP))
        assertEquals("WebRTC", ProtocolDetector.getProtocolDisplayName(TransportProtocol.WEBRTC))
        assertEquals("SRT", ProtocolDetector.getProtocolDisplayName(TransportProtocol.SRT))
    }

    @Test
    fun `should check low latency support`() {
        assertTrue(ProtocolDetector.supportsLowLatency(TransportProtocol.WEBRTC))
        assertTrue(ProtocolDetector.supportsLowLatency(TransportProtocol.SRT))
        assertFalse(ProtocolDetector.supportsLowLatency(TransportProtocol.RTMP))
    }
}