# WebRTC推流支持集成技术文档

## 概述

本文档详细描述了在现有RTMP推流SDK基础上集成WebRTC推流能力的技术方案。通过复用现有的音视频采集、编码和流水线架构，实现RTMP和WebRTC双协议支持，为用户提供更灵活的推流选择。

## 1. WebRTC组件接入方案

### 1.1 第三方库选择与引入

#### 推荐方案：Stream WebRTC Android

**选择理由：**
- 高信任度评分 (9.9/10) 和丰富的代码示例 (266个)
- 预编译的WebRTC库，减少构建复杂度
- 提供Kotlin扩展和Jetpack Compose支持
- 活跃的社区维护和文档支持

#### Gradle依赖配置

```gradle
// library/build.gradle
dependencies {
    // 核心WebRTC库
    implementation 'io.getstream:stream-webrtc-android:1.3.9'

    // Kotlin扩展 (可选)
    implementation 'io.getstream:stream-webrtc-android-ktx:1.3.9'

    // UI组件 (可选)
    implementation 'io.getstream:stream-webrtc-android-ui:1.3.9'

    // Compose支持 (可选)
    implementation 'io.getstream:stream-webrtc-android-compose:1.3.9'
}
```

#### 备选方案

如需更轻量级的集成，可考虑：

```gradle
// 官方预编译库 (较小体积)
implementation 'org.webrtc:android:+'

// 或指定具体版本
implementation 'org.webrtc:android:1.0.32006'
```

### 1.2 WebRTC初始化配置

#### 核心初始化流程

```kotlin
// WebRTCManager.kt
class WebRTCManager private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: WebRTCManager? = null

        fun getInstance(context: Context): WebRTCManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WebRTCManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null

    fun initialize() {
        // 1. 初始化WebRTC上下文
        ContextUtils.initialize(context)

        // 2. 创建EGL上下文 (用于硬件加速)
        eglBase = EglBase.create()

        // 3. 配置初始化选项
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setInjectableLogger({ message, severity, tag ->
                LogHelper.d(tag, message)
            }, Logging.Severity.LS_INFO)
            .createInitializationOptions()

        // 4. 初始化PeerConnectionFactory
        PeerConnectionFactory.initialize(initializationOptions)

        // 5. 创建PeerConnectionFactory
        peerConnectionFactory = createPeerConnectionFactory()
    }

    private fun createPeerConnectionFactory(): PeerConnectionFactory {
        val options = PeerConnectionFactory.Options()

        // 创建编码器工厂 (支持硬件加速)
        val encoderFactory = DefaultVideoEncoderFactory(
            eglBase?.eglBaseContext,
            true, // enableIntelVp8Encoder
            true  // enableH264HighProfile
        )

        // 创建解码器工厂
        val decoderFactory = DefaultVideoDecoderFactory(eglBase?.eglBaseContext)

        // 创建音频设备模块
        val audioDeviceModule = JavaAudioDeviceModule.builder(context)
            .setAudioRecordErrorCallback { errorMessage ->
                LogHelper.e("WebRTC", "Audio record error: $errorMessage")
            }
            .setAudioTrackErrorCallback { errorMessage ->
                LogHelper.e("WebRTC", "Audio track error: $errorMessage")
            }
            .createAudioDeviceModule()

        return PeerConnectionFactory.builder()
            .setOptions(options)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setAudioDeviceModule(audioDeviceModule)
            .createPeerConnectionFactory()
    }

    fun release() {
        peerConnectionFactory?.dispose()
        eglBase?.release()
        PeerConnectionFactory.shutdownInternalTracer()
    }
}
```

### 1.3 权限和安全配置

#### 必需权限

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<!-- WebRTC特有权限 -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
```

#### 网络安全配置

```xml
<!-- res/xml/network_security_config.xml -->
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <!-- 允许本地测试 -->
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">10.0.2.2</domain>
    </domain-config>

    <!-- 生产环境强制HTTPS -->
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system"/>
        </trust-anchors>
    </base-config>
</network-security-config>
```

## 2. SDK接口架构设计

### 2.1 统一推流接口抽象

#### 核心接口定义

```kotlin
// 推流协议枚举
enum class StreamingProtocol {
    RTMP,
    WEBRTC
}

// 统一推流会话接口
interface UnifiedLiveStreamSession {
    // 基础配置
    fun setAudioConfiguration(config: AudioConfiguration)
    fun setVideoConfiguration(config: VideoConfiguration)
    fun setStreamingProtocol(protocol: StreamingProtocol)

    // 生命周期管理
    fun prepare(context: Context, textureId: Int, eglContext: EGLContext?)
    fun start()
    fun pause()
    fun resume()
    fun stop()
    fun release()

    // 动态控制
    fun setMute(isMute: Boolean)
    fun setVideoBps(bps: Int)
    fun switchCamera()
    fun setWatermark(watermark: Watermark)

    // 连接管理
    fun setConnectionTarget(target: ConnectionTarget)
    fun setConnectionListener(listener: ConnectionListener?)
    fun setStatsListener(listener: StatsListener?)
}

// 连接目标抽象
sealed class ConnectionTarget {
    data class RtmpTarget(val url: String) : ConnectionTarget()
    data class WebRtcTarget(
        val signalingUrl: String,
        val stunServers: List<String> = emptyList(),
        val turnServers: List<TurnServer> = emptyList()
    ) : ConnectionTarget()
}

// TURN服务器配置
data class TurnServer(
    val url: String,
    val username: String,
    val credential: String
)
```

#### 连接状态管理

```kotlin
// 连接状态枚举
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED
}

// 连接监听接口
interface ConnectionListener {
    fun onConnectionStateChanged(state: ConnectionState)
    fun onConnectionError(error: ConnectionError)
    fun onConnectionStats(stats: ConnectionStats)
}

// 连接错误定义
sealed class ConnectionError {
    data class NetworkError(val message: String) : ConnectionError()
    data class AuthenticationError(val message: String) : ConnectionError()
    data class ProtocolError(val message: String) : ConnectionError()
    data class ConfigurationError(val message: String) : ConnectionError()
}

// 连接统计信息
data class ConnectionStats(
    val protocol: StreamingProtocol,
    val bitrate: Int,
    val fps: Int,
    val rtt: Long?,
    val packetLoss: Float?,
    val jitter: Long?
)
```

### 2.2 WebRTC特有配置扩展

#### WebRTC配置对象

```kotlin
// WebRTC特有配置
data class WebRtcConfiguration(
    // ICE服务器配置
    val iceServers: List<IceServer> = defaultIceServers(),

    // 连接策略
    val iceTransportPolicy: IceTransportPolicy = IceTransportPolicy.ALL,
    val bundlePolicy: BundlePolicy = BundlePolicy.BALANCED,
    val rtcpMuxPolicy: RtcpMuxPolicy = RtcpMuxPolicy.REQUIRE,

    // 媒体约束
    val audioCodecPreferences: List<AudioCodec> = listOf(AudioCodec.OPUS, AudioCodec.G722),
    val videoCodecPreferences: List<VideoCodec> = listOf(VideoCodec.VP8, VideoCodec.H264),

    // 带宽控制
    val maxBandwidth: Int? = null,
    val minBandwidth: Int? = null,

    // 安全配置
    val dtlsSrtpKeyAgreement: Boolean = true,
    val enableDscp: Boolean = false
) {
    companion object {
        fun defaultIceServers(): List<IceServer> = listOf(
            IceServer("stun:stun.l.google.com:19302"),
            IceServer("stun:stun1.l.google.com:19302")
        )
    }
}

// ICE服务器配置
data class IceServer(
    val url: String,
    val username: String? = null,
    val credential: String? = null
)

// 编解码器枚举扩展
enum class AudioCodec(val mimeType: String, val clockRate: Int) {
    OPUS("audio/opus", 48000),
    G722("audio/G722", 16000),
    PCMU("audio/PCMU", 8000),
    PCMA("audio/PCMA", 8000)
}

enum class VideoCodec(val mimeType: String) {
    VP8("video/VP8"),
    VP9("video/VP9"),
    H264("video/H264"),
    AV1("video/AV1")
}
```

### 2.3 工厂模式实现

#### 推流会话工厂

```kotlin
// 推流会话工厂
object LiveStreamSessionFactory {

    fun createSession(protocol: StreamingProtocol): UnifiedLiveStreamSession {
        return when (protocol) {
            StreamingProtocol.RTMP -> RtmpLiveStreamSession()
            StreamingProtocol.WEBRTC -> WebRtcLiveStreamSession()
        }
    }

    fun createAdaptiveSession(): AdaptiveLiveStreamSession {
        return AdaptiveLiveStreamSession()
    }
}

// 自适应推流会话 (支持协议切换)
class AdaptiveLiveStreamSession : UnifiedLiveStreamSession {
    private var currentSession: UnifiedLiveStreamSession? = null
    private var currentProtocol: StreamingProtocol? = null

    override fun setStreamingProtocol(protocol: StreamingProtocol) {
        if (currentProtocol != protocol) {
            currentSession?.release()
            currentSession = LiveStreamSessionFactory.createSession(protocol)
            currentProtocol = protocol
        }
    }

    // 协议切换逻辑
    fun switchProtocol(newProtocol: StreamingProtocol, seamless: Boolean = false) {
        if (seamless) {
            // 无缝切换实现
            performSeamlessSwitch(newProtocol)
        } else {
            // 停止当前会话，启动新会话
            performHardSwitch(newProtocol)
        }
    }

    private fun performSeamlessSwitch(newProtocol: StreamingProtocol) {
        // 实现无缝协议切换逻辑
        // 1. 准备新会话
        // 2. 同步状态
        // 3. 切换数据流
        // 4. 释放旧会话
    }

    private fun performHardSwitch(newProtocol: StreamingProtocol) {
        stop()
        setStreamingProtocol(newProtocol)
        start()
    }
}
```

## 3. 音视频链路设计

### 3.1 统一数据流抽象

#### 媒体帧定义

```kotlin
// 统一媒体帧接口
interface MediaFrame {
    val timestamp: Long
    val duration: Long
    val isKeyFrame: Boolean
}

// 音频帧
data class AudioFrame(
    override val timestamp: Long,
    override val duration: Long,
    val buffer: ByteBuffer,
    val sampleRate: Int,
    val channels: Int,
    val format: AudioFormat
) : MediaFrame {
    override val isKeyFrame: Boolean = false
}

// 视频帧
data class VideoFrame(
    override val timestamp: Long,
    override val duration: Long,
    val buffer: ByteBuffer,
    val width: Int,
    val height: Int,
    val format: VideoFormat,
    override val isKeyFrame: Boolean
) : MediaFrame

// 原始视频帧 (用于WebRTC)
data class RawVideoFrame(
    override val timestamp: Long,
    override val duration: Long,
    val textureId: Int,
    val transformMatrix: FloatArray,
    val width: Int,
    val height: Int
) : MediaFrame {
    override val isKeyFrame: Boolean = false
}
```

#### 媒体流水线重构

```kotlin
// 统一流水线节点接口
interface MediaPipelineNode {
    val name: String
    val inputPads: List<MediaInputPad>
    val outputPads: List<MediaOutputPad>

    fun start()
    fun stop()
    fun pause()
    fun resume()
    fun release()
}

// 媒体输入端口
interface MediaInputPad {
    fun acceptFrame(frame: MediaFrame)
    fun getAcceptedTypes(): Set<Class<out MediaFrame>>
}

// 媒体输出端口
interface MediaOutputPad {
    fun setDownstreamPad(pad: MediaInputPad)
    fun removeDownstreamPad(pad: MediaInputPad)
    fun pushFrame(frame: MediaFrame)
}

// 流水线管理器
class UnifiedMediaPipeline {
    private val nodes = mutableListOf<MediaPipelineNode>()
    private val connections = mutableMapOf<MediaOutputPad, List<MediaInputPad>>()

    fun addNode(node: MediaPipelineNode) {
        nodes.add(node)
    }

    fun connect(output: MediaOutputPad, input: MediaInputPad) {
        output.setDownstreamPad(input)
        connections[output] = connections.getOrDefault(output, emptyList()) + input
    }

    fun start() {
        nodes.forEach { it.start() }
    }

    fun stop() {
        nodes.forEach { it.stop() }
    }
}
```

### 3.2 复用现有组件的适配层

#### 音频采集适配器

```kotlin
// 音频采集节点适配器
class AudioCaptureNodeAdapter(
    private val audioProcessor: AudioProcessor
) : MediaPipelineNode {

    override val name = "AudioCapture"
    override val inputPads: List<MediaInputPad> = emptyList()
    override val outputPads: List<MediaOutputPad> = listOf(audioOutputPad)

    private val audioOutputPad = object : MediaOutputPad {
        private val downstreamPads = mutableListOf<MediaInputPad>()

        override fun setDownstreamPad(pad: MediaInputPad) {
            downstreamPads.add(pad)
        }

        override fun removeDownstreamPad(pad: MediaInputPad) {
            downstreamPads.remove(pad)
        }

        override fun pushFrame(frame: MediaFrame) {
            downstreamPads.forEach { it.acceptFrame(frame) }
        }
    }

    init {
        // 适配现有AudioProcessor的回调
        audioProcessor.setAudioDataListener(object : OnAudioDataListener {
            override fun onAudioData(bb: ByteBuffer, bi: MediaCodec.BufferInfo) {
                val audioFrame = AudioFrame(
                    timestamp = bi.presentationTimeUs,
                    duration = calculateDuration(bi),
                    buffer = bb,
                    sampleRate = audioProcessor.sampleRate,
                    channels = audioProcessor.channels,
                    format = AudioFormat.PCM
                )
                audioOutputPad.pushFrame(audioFrame)
            }

            override fun onAudioOutformat(outputFormat: MediaFormat?) {
                // 处理格式变化
            }

            override fun onError(error: String?) {
                LogHelper.e(name, "Audio capture error: $error")
            }
        })
    }
}
```

#### 视频采集适配器

```kotlin
// 视频采集节点适配器
class VideoCaptureNodeAdapter(
    private val cameraRecorder: CameraRecorder,
    private val cameraRenderer: CameraRenderer
) : MediaPipelineNode {

    override val name = "VideoCapture"
    override val inputPads: List<MediaInputPad> = emptyList()
    override val outputPads: List<MediaOutputPad> = listOf(encodedOutputPad, rawOutputPad)

    // 编码后视频输出 (用于RTMP)
    private val encodedOutputPad = object : MediaOutputPad {
        private val downstreamPads = mutableListOf<MediaInputPad>()

        override fun setDownstreamPad(pad: MediaInputPad) {
            downstreamPads.add(pad)
        }

        override fun removeDownstreamPad(pad: MediaInputPad) {
            downstreamPads.remove(pad)
        }

        override fun pushFrame(frame: MediaFrame) {
            downstreamPads.forEach { it.acceptFrame(frame) }
        }
    }

    // 原始视频输出 (用于WebRTC)
    private val rawOutputPad = object : MediaOutputPad {
        private val downstreamPads = mutableListOf<MediaInputPad>()

        override fun setDownstreamPad(pad: MediaInputPad) {
            downstreamPads.add(pad)
        }

        override fun removeDownstreamPad(pad: MediaInputPad) {
            downstreamPads.remove(pad)
        }

        override fun pushFrame(frame: MediaFrame) {
            downstreamPads.forEach { it.acceptFrame(frame) }
        }
    }

    init {
        // 适配编码后的视频数据
        cameraRecorder.setVideoDataListener(object : OnVideoEncodeListener {
            override fun onVideoEncode(bb: ByteBuffer?, bi: MediaCodec.BufferInfo?) {
                if (bb != null && bi != null) {
                    val videoFrame = VideoFrame(
                        timestamp = bi.presentationTimeUs,
                        duration = calculateDuration(bi),
                        buffer = bb,
                        width = cameraRecorder.width,
                        height = cameraRecorder.height,
                        format = VideoFormat.H264,
                        isKeyFrame = (bi.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0
                    )
                    encodedOutputPad.pushFrame(videoFrame)
                }
            }

            override fun onVideoOutformat(outputFormat: MediaFormat?) {
                // 处理格式变化
            }
        })

        // 适配原始纹理数据 (用于WebRTC)
        cameraRenderer.setFrameAvailableListener { textureId, transformMatrix ->
            val rawFrame = RawVideoFrame(
                timestamp = System.nanoTime() / 1000,
                duration = 33333, // 假设30fps
                textureId = textureId,
                transformMatrix = transformMatrix,
                width = cameraRenderer.width,
                height = cameraRenderer.height
            )
            rawOutputPad.pushFrame(rawFrame)
        }
    }
}
```

### 3.3 协议特定的传输节点

#### RTMP传输节点

```kotlin
// RTMP传输节点 (复用现有实现)
class RtmpTransportNode(
    private val streamSession: RtmpStreamSession
) : MediaPipelineNode {

    override val name = "RtmpTransport"
    override val inputPads: List<MediaInputPad> = listOf(audioInputPad, videoInputPad)
    override val outputPads: List<MediaOutputPad> = emptyList()

    private val audioInputPad = object : MediaInputPad {
        override fun acceptFrame(frame: MediaFrame) {
            if (frame is AudioFrame) {
                streamSession.pushAudio(frame.buffer, createBufferInfo(frame))
            }
        }

        override fun getAcceptedTypes(): Set<Class<out MediaFrame>> {
            return setOf(AudioFrame::class.java)
        }
    }

    private val videoInputPad = object : MediaInputPad {
        override fun acceptFrame(frame: MediaFrame) {
            if (frame is VideoFrame) {
                streamSession.pushVideo(frame.buffer, createBufferInfo(frame))
            }
        }

        override fun getAcceptedTypes(): Set<Class<out MediaFrame>> {
            return setOf(VideoFrame::class.java)
        }
    }

    private fun createBufferInfo(frame: MediaFrame): MediaCodec.BufferInfo {
        return MediaCodec.BufferInfo().apply {
            presentationTimeUs = frame.timestamp
            size = when (frame) {
                is AudioFrame -> frame.buffer.remaining()
                is VideoFrame -> frame.buffer.remaining()
                else -> 0
            }
            flags = if (frame.isKeyFrame) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
        }
    }
}
```

#### WebRTC传输节点

```kotlin
// WebRTC传输节点
class WebRtcTransportNode(
    private val peerConnection: PeerConnection,
    private val peerConnectionFactory: PeerConnectionFactory
) : MediaPipelineNode {

    override val name = "WebRtcTransport"
    override val inputPads: List<MediaInputPad> = listOf(audioInputPad, rawVideoInputPad)
    override val outputPads: List<MediaOutputPad> = emptyList()

    private var audioTrack: AudioTrack? = null
    private var videoTrack: VideoTrack? = null
    private var videoSource: VideoSource? = null
    private var videoCapturer: CustomVideoCapturer? = null

    private val audioInputPad = object : MediaInputPad {
        override fun acceptFrame(frame: MediaFrame) {
            if (frame is AudioFrame) {
                // WebRTC音频通过AudioTrack自动处理
                // 这里可以添加音频数据统计
            }
        }

        override fun getAcceptedTypes(): Set<Class<out MediaFrame>> {
            return setOf(AudioFrame::class.java)
        }
    }

    private val rawVideoInputPad = object : MediaInputPad {
        override fun acceptFrame(frame: MediaFrame) {
            if (frame is RawVideoFrame) {
                videoCapturer?.onFrameAvailable(frame)
            }
        }

        override fun getAcceptedTypes(): Set<Class<out MediaFrame>> {
            return setOf(RawVideoFrame::class.java)
        }
    }

    override fun start() {
        setupAudioTrack()
        setupVideoTrack()
    }

    private fun setupAudioTrack() {
        val audioSource = peerConnectionFactory.createAudioSource(MediaConstraints())
        audioTrack = peerConnectionFactory.createAudioTrack("audio_track", audioSource)
        peerConnection.addTrack(audioTrack, listOf("stream_id"))
    }

    private fun setupVideoTrack() {
        videoCapturer = CustomVideoCapturer()
        videoSource = peerConnectionFactory.createVideoSource(videoCapturer!!.isScreencast)
        videoCapturer!!.initialize(
            SurfaceTextureHelper.create("video_capture_thread", null),
            context,
            videoSource!!.capturerObserver
        )

        videoTrack = peerConnectionFactory.createVideoTrack("video_track", videoSource)
        peerConnection.addTrack(videoTrack, listOf("stream_id"))
    }

    override fun stop() {
        audioTrack?.dispose()
        videoTrack?.dispose()
        videoSource?.dispose()
        videoCapturer?.dispose()
    }
}

// 自定义视频采集器 (接收纹理数据)
class CustomVideoCapturer : VideoCapturer {
    private var capturerObserver: CapturerObserver? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null

    override val isScreencast: Boolean = false

    fun onFrameAvailable(frame: RawVideoFrame) {
        val videoFrame = VideoFrame(
            VideoFrame.TextureBuffer.createTextureBuffer(
                frame.width,
                frame.height,
                VideoFrame.TextureBuffer.Type.OES,
                frame.textureId,
                Matrix()
            ),
            0, // rotation
            frame.timestamp * 1000 // convert to nanoseconds
        )

        capturerObserver?.onFrameCaptured(videoFrame)
        videoFrame.release()
    }

    override fun initialize(
        surfaceTextureHelper: SurfaceTextureHelper?,
        context: Context?,
        capturerObserver: CapturerObserver?
    ) {
        this.surfaceTextureHelper = surfaceTextureHelper
        this.capturerObserver = capturerObserver
    }

    override fun startCapture(width: Int, height: Int, framerate: Int) {
        // 启动采集 (由外部驱动)
    }

    override fun stopCapture() {
        // 停止采集
    }

    override fun changeCaptureFormat(width: Int, height: Int, framerate: Int) {
        // 改变采集格式
    }

    override fun dispose() {
        surfaceTextureHelper?.dispose()
    }

    override fun isRunning(): Boolean = true
}
```

### 3.4 解耦和复用策略

#### 接口隔离原则

```kotlin
// 将大接口拆分为小接口
interface MediaCapturer {
    fun start()
    fun stop()
    fun release()
}

interface AudioCapturer : MediaCapturer {
    fun setAudioConfiguration(config: AudioConfiguration)
    fun setMute(mute: Boolean)
    fun setAudioDataListener(listener: AudioDataListener)
}

interface VideoCapturer : MediaCapturer {
    fun setVideoConfiguration(config: VideoConfiguration)
    fun setWatermark(watermark: Watermark)
    fun setVideoDataListener(listener: VideoDataListener)
    fun switchCamera()
}

// 编码器接口
interface MediaEncoder {
    fun configure(config: MediaConfiguration)
    fun start()
    fun stop()
    fun release()
}

interface AudioEncoder : MediaEncoder {
    fun encode(audioData: ByteArray): ByteArray?
}

interface VideoEncoder : MediaEncoder {
    fun encode(surface: Surface): Surface
}
```

#### 依赖注入容器

```kotlin
// 简单的依赖注入容器
object MediaComponentContainer {
    private val components = mutableMapOf<Class<*>, Any>()
    private val factories = mutableMapOf<Class<*>, () -> Any>()

    inline fun <reified T> register(instance: T) {
        components[T::class.java] = instance as Any
    }

    inline fun <reified T> registerFactory(noinline factory: () -> T) {
        factories[T::class.java] = factory
    }

    inline fun <reified T> get(): T {
        return components[T::class.java] as? T
            ?: factories[T::class.java]?.invoke() as? T
            ?: throw IllegalStateException("Component ${T::class.java} not registered")
    }

    fun clear() {
        components.clear()
        factories.clear()
    }
}

// 使用示例
fun setupDependencies() {
    MediaComponentContainer.apply {
        // 注册音频组件
        registerFactory<AudioCapturer> {
            AudioCapturerImpl(get<AudioProcessor>())
        }

        // 注册视频组件
        registerFactory<VideoCapturer> {
            VideoCapturerImpl(get<CameraRecorder>(), get<CameraRenderer>())
        }

        // 注册传输组件
        registerFactory<RtmpTransport> {
            RtmpTransportImpl(get<RtmpStreamSession>())
        }

        registerFactory<WebRtcTransport> {
            WebRtcTransportImpl(get<PeerConnection>())
        }
    }
}
```

#### 配置驱动的组件选择

```kotlin
// 配置驱动的流水线构建器
class MediaPipelineBuilder {
    private val config: StreamingConfiguration

    constructor(config: StreamingConfiguration) {
        this.config = config
    }

    fun buildPipeline(): UnifiedMediaPipeline {
        val pipeline = UnifiedMediaPipeline()

        // 添加音频采集节点
        val audioCapture = createAudioCaptureNode()
        pipeline.addNode(audioCapture)

        // 添加视频采集节点
        val videoCapture = createVideoCaptureNode()
        pipeline.addNode(videoCapture)

        // 根据协议添加传输节点
        val transport = when (config.protocol) {
            StreamingProtocol.RTMP -> createRtmpTransportNode()
            StreamingProtocol.WEBRTC -> createWebRtcTransportNode()
        }
        pipeline.addNode(transport)

        // 连接节点
        connectNodes(pipeline, audioCapture, videoCapture, transport)

        return pipeline
    }

    private fun connectNodes(
        pipeline: UnifiedMediaPipeline,
        audioCapture: MediaPipelineNode,
        videoCapture: MediaPipelineNode,
        transport: MediaPipelineNode
    ) {
        when (config.protocol) {
            StreamingProtocol.RTMP -> {
                // RTMP使用编码后的数据
                pipeline.connect(audioCapture.outputPads[0], transport.inputPads[0])
                pipeline.connect(videoCapture.outputPads[0], transport.inputPads[1])
            }
            StreamingProtocol.WEBRTC -> {
                // WebRTC使用原始数据
                pipeline.connect(audioCapture.outputPads[0], transport.inputPads[0])
                pipeline.connect(videoCapture.outputPads[1], transport.inputPads[1])
            }
        }
    }
}
```

## 4. 实现路线图

### 4.1 第一阶段：基础WebRTC集成 (2-3周)

**目标：** 实现基本的WebRTC推流功能

**任务清单：**
1. **依赖集成** (3天)
   - 添加WebRTC库依赖
   - 配置构建脚本和权限
   - 验证库的正确加载

2. **核心组件实现** (7天)
   - 实现WebRTCManager初始化
   - 创建PeerConnection管理器
   - 实现基础的音视频轨道创建

3. **接口适配** (5天)
   - 实现WebRtcLiveStreamSession
   - 适配现有的音视频采集组件
   - 创建基础的信令处理

4. **测试验证** (3天)
   - 单元测试
   - 集成测试
   - 基础功能验证

### 4.2 第二阶段：流水线重构 (3-4周)

**目标：** 重构现有架构，支持双协议

**任务清单：**
1. **流水线抽象** (7天)
   - 设计统一的媒体流水线接口
   - 实现MediaPipelineNode抽象
   - 创建流水线管理器

2. **组件适配器** (10天)
   - 实现音频采集适配器
   - 实现视频采集适配器
   - 实现传输节点适配器

3. **配置系统扩展** (5天)
   - 扩展配置对象支持WebRTC参数
   - 实现配置验证和降级
   - 创建配置驱动的组件工厂

4. **集成测试** (3天)
   - 双协议切换测试
   - 性能基准测试
   - 稳定性测试

### 4.3 第三阶段：高级特性 (2-3周)

**目标：** 实现高级功能和优化

**任务清单：**
1. **连接管理** (7天)
   - 实现ICE连接管理
   - 添加重连机制
   - 实现连接质量监控

2. **编解码器优化** (7天)
   - 支持VP8/VP9编码器
   - 实现硬件加速优化
   - 添加编码参数自适应

3. **UI集成** (3天)
   - 更新AVLiveView支持WebRTC
   - 添加协议切换UI
   - 实现统计信息显示

### 4.4 第四阶段：生产就绪 (1-2周)

**目标：** 生产环境优化和文档完善

**任务清单：**
1. **性能优化** (5天)
   - 内存使用优化
   - CPU使用优化
   - 网络传输优化

2. **错误处理** (3天)
   - 完善错误处理机制
   - 添加降级策略
   - 实现用户友好的错误提示

3. **文档和示例** (4天)
   - 完善API文档
   - 创建使用示例
   - 编写最佳实践指南

## 5. 注意事项和风险

### 5.1 技术风险

**1. WebRTC库兼容性**
- **风险：** 不同WebRTC库版本可能存在API变化
- **缓解：** 选择稳定版本，建立兼容性测试套件

**2. 性能影响**
- **风险：** 双协议支持可能增加CPU和内存占用
- **缓解：** 实现懒加载，按需初始化WebRTC组件

**3. 编解码器兼容性**
- **风险：** 不同设备对VP8/VP9支持程度不同
- **缓解：** 实现编解码器能力检测和降级策略

### 5.2 架构风险

**1. 过度抽象**
- **风险：** 过度的抽象可能导致性能损失和复杂度增加
- **缓解：** 保持接口简单，避免不必要的抽象层

**2. 状态管理复杂性**
- **风险：** 双协议状态管理可能变得复杂
- **缓解：** 使用状态机模式，清晰定义状态转换

### 5.3 用户体验风险

**1. 协议切换延迟**
- **风险：** 协议切换可能导致推流中断
- **缓解：** 实现无缝切换机制，提供用户反馈

**2. 配置复杂性**
- **风险：** 过多的配置选项可能困扰用户
- **缓解：** 提供预设配置，使用智能默认值

## 6. 总结

本技术文档提供了在现有RTMP推流SDK基础上集成WebRTC能力的完整方案。通过复用现有的音视频采集和编码组件，设计统一的流水线架构，可以高效地实现双协议支持。

**核心优势：**
1. **最大化复用** - 充分利用现有的音视频处理组件
2. **架构清晰** - 通过接口抽象实现协议无关的设计
3. **扩展性强** - 流水线架构便于添加新的处理节点
4. **用户友好** - 统一的API接口简化用户使用

**实施建议：**
1. 采用渐进式实施策略，分阶段完成集成
2. 重视测试，确保双协议的稳定性和兼容性
3. 关注性能优化，避免功能增加带来的性能损失
4. 完善文档和示例，降低用户学习成本

通过遵循本文档的设计方案和实施路线图，可以成功地为现有SDK添加WebRTC推流能力，为用户提供更灵活、更现代的推流解决方案。
