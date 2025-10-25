package com.astrastream.avpush.unified

import com.astrastream.avpush.unified.builder.createStreamSession
import com.astrastream.avpush.unified.config.*
import com.astrastream.avpush.unified.transport.TransportRegistry
import com.astrastream.avpush.unified.transport.rtmp.RtmpTransportFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * 统一推流会话测试
 */
@ExperimentalCoroutinesApi
class UnifiedStreamSessionTest {

    @Before
    fun setup() {
        // 注册传输工厂
        TransportRegistry.registerFactory(RtmpTransportFactory())
    }

    @Test
    fun `should create session with default configuration`() {
        val session = createStreamSession {
            addRtmp(TestConstants.RTMP_TEST_URL)
        }

        assertNotNull(session)
        assertEquals(SessionState.IDLE, session.state.value)
    }

    @Test
    fun `should create session with custom video configuration`() {
        val session = createStreamSession {
            video {
                width = 1920
                height = 1080
                frameRate = 60
                bitrate = 4_000_000
                codec = VideoCodec.H264
                profile = VideoProfile.HIGH
            }

            audio {
                sampleRate = 48000
                bitrate = 192_000
                channels = 2
                codec = AudioCodec.AAC
                enableAEC = true
            }

            camera {
                facing = CameraFacing.FRONT
                autoFocus = true
                stabilization = true
            }

            addRtmp(TestConstants.RTMP_TEST_URL)
        }

        assertNotNull(session)
        assertEquals(SessionState.IDLE, session.state.value)
    }

    @Test
    fun `should support multiple transport protocols`() {
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

            // 添加多个传输协议
            val rtmpId = addRtmp(TestConstants.RTMP_TEST_URL) {
                priority = 1
                enableLowLatency = false
            }

            // 注意：WebRTC需要单独的实现，这里仅作为示例
            // val webrtcId = addWebRtc("wss://signal.example.com", "room123") {
            //     priority = 0
            //     audioCodec = AudioCodec.OPUS
            //     videoCodec = VideoCodec.H264
            // }

            advanced {
                enableSimultaneousPush = true
                fallbackEnabled = true
            }
        }

        assertNotNull(session)
    }

    @Test
    fun `should validate transport configuration`() {
        val factory = RtmpTransportFactory()

        // 有效配置
        val validConfig = RtmpConfig(
            pushUrl = TestConstants.RTMP_TEST_URL,
            connectTimeout = java.time.Duration.ofSeconds(10),
            chunkSize = 4096
        )

        val validResult = factory.validateConfig(validConfig)
        assertTrue("Valid config should pass validation", validResult.isValid)
        assertTrue("Valid config should have no errors", validResult.errors.isEmpty())

        // 无效配置 - 空URL
        val invalidConfig = RtmpConfig(
            pushUrl = "",
            connectTimeout = java.time.Duration.ofSeconds(10)
        )

        val invalidResult = factory.validateConfig(invalidConfig)
        assertFalse("Invalid config should fail validation", invalidResult.isValid)
        assertFalse("Invalid config should have errors", invalidResult.errors.isEmpty())
    }

    @Test
    fun `should handle configuration updates`() = runTest {
        val session = createStreamSession {
            video {
                width = 1280
                height = 720
                frameRate = 30
                bitrate = 2_000_000
            }

            addRtmp(TestConstants.RTMP_TEST_URL)
        }

        // 更新视频配置
        val newVideoConfig = VideoConfig(
            width = 1920,
            height = 1080,
            frameRate = 60,
            bitrate = 4_000_000,
            codec = VideoCodec.H264
        )

        session.updateVideoConfig(newVideoConfig)

        // 更新音频配置
        val newAudioConfig = AudioConfig(
            sampleRate = 48000,
            bitrate = 192_000,
            channels = 2,
            codec = AudioCodec.AAC,
            enableAEC = true,
            enableAGC = true
        )

        session.updateAudioConfig(newAudioConfig)

        // 验证配置更新不会改变会话状态
        assertEquals(SessionState.IDLE, session.state.value)
    }

    @Test
    fun `should support watermark configuration`() {
        val session = createStreamSession {
            video {
                width = 1280
                height = 720
                frameRate = 30
                bitrate = 2_000_000
            }

            addRtmp(TestConstants.RTMP_TEST_URL)
        }

        // 文字水印
        val textWatermark = TextWatermark(
            text = "Live Stream",
            color = android.graphics.Color.WHITE,
            textSize = 48f,
            position = WatermarkPosition.TOP_RIGHT,
            margin = 20,
            alpha = 0.8f
        )

        session.setWatermark(textWatermark)

        // 验证水印设置不会改变会话状态
        assertEquals(SessionState.IDLE, session.state.value)
    }

    @Test
    fun `should handle transport management`() {
        val session = createStreamSession {
            addRtmp(TestConstants.RTMP_TEST_URL) {
                priority = 0
            }
        }

        // 添加备用传输
        val backupTransportId = session.addTransport(
            RtmpConfig(
                pushUrl = TestConstants.RTMP_BACKUP_URL,
                priority = 1
            )
        )

        assertNotNull(backupTransportId)

        // 切换主传输
        session.switchPrimaryTransport(backupTransportId)

        // 移除传输
        session.removeTransport(backupTransportId)
    }

    @Test
    fun `should validate video configuration constraints`() {
        // 测试有效的视频配置
        val validConfig = VideoConfig(
            width = 1280,
            height = 720,
            frameRate = 30,
            bitrate = 2_000_000,
            minBitrate = 500_000,
            maxBitrate = 4_000_000
        )

        assertTrue("Valid video config should be valid", validConfig.isValidResolution())

        // 测试无效的视频配置
        assertThrows("Width must be positive", IllegalArgumentException::class.java) {
            VideoConfig(width = 0, height = 720)
        }

        assertThrows("Height must be positive", IllegalArgumentException::class.java) {
            VideoConfig(width = 1280, height = 0)
        }

        assertThrows("Min bitrate must be <= bitrate", IllegalArgumentException::class.java) {
            VideoConfig(
                width = 1280,
                height = 720,
                bitrate = 1_000_000,
                minBitrate = 2_000_000
            )
        }
    }

    @Test
    fun `should validate audio configuration constraints`() {
        // 测试有效的音频配置
        val validConfig = AudioConfig(
            sampleRate = 44100,
            bitrate = 128_000,
            channels = 2
        )

        assertNotNull(validConfig)

        // 测试无效的采样率
        assertThrows("Unsupported sample rate", IllegalArgumentException::class.java) {
            AudioConfig(sampleRate = 12345)
        }

        // 测试无效的码率
        assertThrows("Audio bitrate must be between 32k and 320k", IllegalArgumentException::class.java) {
            AudioConfig(bitrate = 500_000)
        }

        // 测试无效的声道数
        assertThrows("Channels must be 1 or 2", IllegalArgumentException::class.java) {
            AudioConfig(channels = 3)
        }
    }

    @Test
    fun `should handle session state transitions`() {
        val session = createStreamSession {
            addRtmp(TestConstants.RTMP_TEST_URL)
        }

        // 初始状态应该是IDLE
        assertEquals(SessionState.IDLE, session.state.value)

        // 测试状态转换逻辑
        assertTrue("Should be able to transition from IDLE to PREPARING",
            SessionState.IDLE.canTransitionTo(SessionState.PREPARING))

        assertTrue("Should be able to transition from PREPARING to PREPARED",
            SessionState.PREPARING.canTransitionTo(SessionState.PREPARED))

        assertTrue("Should be able to transition from PREPARED to STREAMING",
            SessionState.PREPARED.canTransitionTo(SessionState.STREAMING))

        assertTrue("Should be able to transition from STREAMING to STOPPING",
            SessionState.STREAMING.canTransitionTo(SessionState.STOPPING))

        assertTrue("Should be able to transition from STOPPING to IDLE",
            SessionState.STOPPING.canTransitionTo(SessionState.IDLE))

        // 测试无效的状态转换
        assertFalse("Should not be able to transition from IDLE to STREAMING",
            SessionState.IDLE.canTransitionTo(SessionState.STREAMING))

        assertFalse("Should not be able to transition from PREPARED to STOPPING",
            SessionState.PREPARED.canTransitionTo(SessionState.STOPPING))
    }

    @Test
    fun `should support advanced configuration`() {
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

            addRtmp(TestConstants.RTMP_TEST_URL)
            addRtmp(TestConstants.RTMP_BACKUP_URL)

            advanced {
                enableSimultaneousPush = true
                primaryTransport = TransportProtocol.RTMP
                fallbackEnabled = true
                autoSwitchThreshold = ConnectionQuality.FAIR
                enableMetrics = true
                metricsInterval = java.time.Duration.ofSeconds(1)
                bufferStrategy = com.astrastream.avpush.unified.builder.BufferStrategy.ADAPTIVE
                enableGpuAcceleration = true
                enableMemoryPool = true
                maxBufferSize = 50 * 1024 * 1024 // 50MB
            }
        }

        assertNotNull(session)
        assertEquals(SessionState.IDLE, session.state.value)
    }

    @Test
    fun `should handle retry policy configuration`() {
        val exponentialBackoff = RetryPolicy.exponentialBackoff(
            maxRetries = 5,
            baseDelay = java.time.Duration.ofSeconds(1),
            maxDelay = java.time.Duration.ofSeconds(30)
        )

        assertEquals(5, exponentialBackoff.maxRetries)
        assertEquals(java.time.Duration.ofSeconds(1), exponentialBackoff.baseDelay)
        assertEquals(java.time.Duration.ofSeconds(30), exponentialBackoff.maxDelay)

        // 测试延迟计算
        val firstDelay = exponentialBackoff.getDelay(0)
        val secondDelay = exponentialBackoff.getDelay(1)

        assertTrue("Second delay should be longer than first delay",
            secondDelay.toMillis() >= firstDelay.toMillis())

        val fixedDelay = RetryPolicy.fixedDelay(
            maxRetries = 3,
            delay = java.time.Duration.ofSeconds(5)
        )

        assertEquals(3, fixedDelay.maxRetries)
        assertEquals(java.time.Duration.ofSeconds(5), fixedDelay.baseDelay)
        assertEquals(java.time.Duration.ofSeconds(5), fixedDelay.maxDelay)

        val noRetry = RetryPolicy.noRetry()
        assertEquals(0, noRetry.maxRetries)
        assertEquals(java.time.Duration.ZERO, noRetry.getDelay(0))
    }
}