package com.astrastream.avpush.unified.config

/**
 * 视频配置
 */
data class VideoConfig(
    val width: Int = 1280,
    val height: Int = 720,
    val frameRate: Int = 30,
    val bitrate: Int = 2_000_000,
    val keyFrameInterval: Int = 2,
    val codec: VideoCodec = VideoCodec.H264,
    val profile: VideoProfile = VideoProfile.BASELINE,
    val level: VideoLevel = VideoLevel.LEVEL_3_1,
    val enableHardwareAcceleration: Boolean = true,
    val bitrateMode: BitrateMode = BitrateMode.VBR,
    val enableAdaptiveBitrate: Boolean = true,
    val minBitrate: Int = 300_000,
    val maxBitrate: Int = 4_000_000,
    val qualityPreset: QualityPreset = QualityPreset.BALANCED,
    val enableBFrames: Boolean = false,
    val maxBFrames: Int = 0,
    val enableLowLatency: Boolean = false,
    val tuning: EncoderTuning = EncoderTuning.DEFAULT,
    val preferredEncoder: String? = null
) {
    init {
        require(width > 0) { "Width must be positive" }
        require(height > 0) { "Height must be positive" }
        require(frameRate > 0) { "Frame rate must be positive" }
        require(bitrate > 0) { "Bitrate must be positive" }
        require(keyFrameInterval > 0) { "Key frame interval must be positive" }
        require(minBitrate <= bitrate) { "Min bitrate must be <= bitrate" }
        require(bitrate <= maxBitrate) { "Bitrate must be <= max bitrate" }
    }

    fun isValidResolution(): Boolean {
        return width % 2 == 0 && height % 2 == 0 // 确保偶数分辨率
    }
}

/**
 * 音频配置
 */
data class AudioConfig(
    val sampleRate: Int = 44100,
    val bitrate: Int = 128_000,
    val channels: Int = 2,
    val codec: AudioCodec = AudioCodec.AAC,
    val profile: AudioProfile = AudioProfile.LC,
    val enableAEC: Boolean = true,
    val enableAGC: Boolean = true,
    val enableNoiseReduction: Boolean = true,
    val enableHighPassFilter: Boolean = false,
    val enableVBR: Boolean = false,
    val bufferSize: Int = 4096,
    val enableLowLatency: Boolean = false,
    val enableWindNoiseReduction: Boolean = false
) {
    init {
        require(sampleRate in listOf(8000, 16000, 22050, 44100, 48000)) {
            "Unsupported sample rate: $sampleRate"
        }
        require(bitrate in 32_000..320_000) {
            "Audio bitrate must be between 32k and 320k"
        }
        require(channels in 1..2) { "Channels must be 1 or 2" }
        require(bufferSize > 0) { "Buffer size must be positive" }
    }
}

/**
 * 摄像头配置
 */
data class CameraConfig(
    val facing: CameraFacing = CameraFacing.BACK,
    val autoFocus: Boolean = true,
    val enableFlash: Boolean = false,
    val stabilization: Boolean = true,
    val exposureMode: ExposureMode = ExposureMode.AUTO,
    val whiteBalanceMode: WhiteBalanceMode = WhiteBalanceMode.AUTO,
    val focusMode: FocusMode = FocusMode.CONTINUOUS_VIDEO,
    val zoomLevel: Float = 1.0f,
    val enableFaceDetection: Boolean = false
) {
    init {
        require(zoomLevel >= 1.0f) { "Zoom level must be >= 1.0" }
    }
}

/**
 * 视频编码配置文件
 */
enum class VideoProfile {
    BASELINE,
    MAIN,
    HIGH
}

/**
 * 视频编码级别
 */
enum class VideoLevel {
    LEVEL_3_0,
    LEVEL_3_1,
    LEVEL_3_2,
    LEVEL_4_0,
    LEVEL_4_1,
    LEVEL_4_2,
    LEVEL_5_0,
    LEVEL_5_1,
    LEVEL_5_2
}

/**
 * 码率控制模式
 */
enum class BitrateMode {
    CBR,  // 恒定码率
    VBR,  // 可变码率
    CQ    // 恒定质量
}

/**
 * 质量预设
 */
enum class QualityPreset {
    ULTRA_FAST,
    SUPER_FAST,
    VERY_FAST,
    FASTER,
    FAST,
    MEDIUM,
    SLOW,
    SLOWER,
    VERY_SLOW,
    BALANCED,
    HIGH_QUALITY,
    LOW_LATENCY
}

/**
 * 编码器调优
 */
enum class EncoderTuning {
    DEFAULT,
    FILM,
    ANIMATION,
    GRAIN,
    STILL_IMAGE,
    FAST_DECODE,
    ZERO_LATENCY,
    GAMING,
    SCREEN_CONTENT
}

/**
 * 音频编码配置文件
 */
enum class AudioProfile {
    LC,      // Low Complexity
    HE,      // High Efficiency
    HE_V2,   // High Efficiency v2
    LD,      // Low Delay
    ELD      // Enhanced Low Delay
}

/**
 * 摄像头朝向
 */
enum class CameraFacing {
    FRONT,
    BACK,
    EXTERNAL
}

/**
 * 曝光模式
 */
enum class ExposureMode {
    AUTO,
    MANUAL,
    LOCKED
}

/**
 * 白平衡模式
 */
enum class WhiteBalanceMode {
    AUTO,
    INCANDESCENT,
    FLUORESCENT,
    WARM_FLUORESCENT,
    DAYLIGHT,
    CLOUDY_DAYLIGHT,
    TWILIGHT,
    SHADE,
    MANUAL
}

/**
 * 对焦模式
 */
enum class FocusMode {
    AUTO,
    INFINITY,
    MACRO,
    FIXED,
    EDOF,
    CONTINUOUS_VIDEO,
    CONTINUOUS_PICTURE
}

/**
 * 水印配置
 */
sealed class Watermark

/**
 * 文字水印
 */
data class TextWatermark(
    val text: String,
    val color: Int,
    val textSize: Float,
    val position: WatermarkPosition,
    val margin: Int = 20,
    val alpha: Float = 1.0f,
    val fontFamily: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false
) : Watermark()

/**
 * 图片水印
 */
data class ImageWatermark(
    val bitmap: android.graphics.Bitmap,
    val position: WatermarkPosition,
    val margin: Int = 20,
    val alpha: Float = 1.0f,
    val scale: Float = 1.0f
) : Watermark()

/**
 * 水印位置
 */
enum class WatermarkPosition {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,
    CENTER
}