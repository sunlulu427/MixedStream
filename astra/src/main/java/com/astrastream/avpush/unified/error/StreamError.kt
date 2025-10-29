package com.astrastream.avpush.unified.error

import com.astrastream.avpush.unified.config.TransportProtocol
import java.time.Instant

/**
 * 推流错误基类
 */
sealed class StreamError {
    abstract val code: String
    abstract val message: String
    abstract val recoverable: Boolean
    abstract val timestamp: Instant

    companion object {
        fun from(throwable: Throwable): StreamError {
            return when (throwable) {
                is SecurityException -> SystemError.PermissionDenied(
                    message = throwable.message ?: "Permission denied"
                )
                is IllegalArgumentException -> ConfigurationError.InvalidParameter(
                    message = throwable.message ?: "Invalid parameter",
                    parameter = "unknown"
                )
                is IllegalStateException -> SystemError.InvalidState(
                    message = throwable.message ?: "Invalid state"
                )
                else -> SystemError.UnknownError(
                    message = throwable.message ?: "Unknown error",
                    cause = throwable
                )
            }
        }
    }
}

/**
 * 传输相关错误
 */
sealed class TransportError : StreamError() {
    data class ConnectionFailed(
        override val code: String = "TRANSPORT_CONNECTION_FAILED",
        override val message: String,
        val transport: TransportProtocol,
        override val timestamp: Instant = Instant.now()
    ) : TransportError() {
        override val recoverable: Boolean = true
    }

    data class AuthenticationFailed(
        override val code: String = "TRANSPORT_AUTH_FAILED",
        override val message: String,
        val transport: TransportProtocol,
        override val timestamp: Instant = Instant.now()
    ) : TransportError() {
        override val recoverable: Boolean = false
    }

    data class NetworkError(
        override val code: String = "NETWORK_ERROR",
        override val message: String,
        val errorCode: Int,
        val transport: TransportProtocol,
        override val timestamp: Instant = Instant.now()
    ) : TransportError() {
        override val recoverable: Boolean = true
    }

    data class TimeoutError(
        override val code: String = "TRANSPORT_TIMEOUT",
        override val message: String,
        val transport: TransportProtocol,
        override val timestamp: Instant = Instant.now()
    ) : TransportError() {
        override val recoverable: Boolean = true
    }

    data class ProtocolError(
        override val code: String = "PROTOCOL_ERROR",
        override val message: String,
        val transport: TransportProtocol,
        override val timestamp: Instant = Instant.now()
    ) : TransportError() {
        override val recoverable: Boolean = false
    }
}

/**
 * 编码相关错误
 */
sealed class EncodingError : StreamError() {
    data class HardwareEncoderFailed(
        override val code: String = "HARDWARE_ENCODER_FAILED",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : EncodingError() {
        override val recoverable: Boolean = true // 可切换到软编码
    }

    data class SoftwareEncoderFailed(
        override val code: String = "SOFTWARE_ENCODER_FAILED",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : EncodingError() {
        override val recoverable: Boolean = false
    }

    data class ConfigurationError(
        override val code: String = "ENCODING_CONFIG_ERROR",
        override val message: String,
        val parameter: String,
        override val timestamp: Instant = Instant.now()
    ) : EncodingError() {
        override val recoverable: Boolean = false
    }

    data class BufferOverflow(
        override val code: String = "ENCODING_BUFFER_OVERFLOW",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : EncodingError() {
        override val recoverable: Boolean = true
    }

    data class FormatNotSupported(
        override val code: String = "FORMAT_NOT_SUPPORTED",
        override val message: String,
        val format: String,
        override val timestamp: Instant = Instant.now()
    ) : EncodingError() {
        override val recoverable: Boolean = false
    }
}

/**
 * 配置相关错误
 */
sealed class ConfigurationError : StreamError() {
    data class InvalidParameter(
        override val code: String = "INVALID_PARAMETER",
        override val message: String,
        val parameter: String,
        override val timestamp: Instant = Instant.now()
    ) : ConfigurationError() {
        override val recoverable: Boolean = false
    }

    data class UnsupportedConfiguration(
        override val code: String = "UNSUPPORTED_CONFIGURATION",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : ConfigurationError() {
        override val recoverable: Boolean = false
    }

    data class ConflictingSettings(
        override val code: String = "CONFLICTING_SETTINGS",
        override val message: String,
        val conflictingParameters: List<String>,
        override val timestamp: Instant = Instant.now()
    ) : ConfigurationError() {
        override val recoverable: Boolean = false
    }
}

/**
 * 系统相关错误
 */
sealed class SystemError : StreamError() {
    data class PermissionDenied(
        override val code: String = "PERMISSION_DENIED",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : SystemError() {
        override val recoverable: Boolean = false
    }

    data class ResourceUnavailable(
        override val code: String = "RESOURCE_UNAVAILABLE",
        override val message: String,
        val resource: String,
        override val timestamp: Instant = Instant.now()
    ) : SystemError() {
        override val recoverable: Boolean = true
    }

    data class OutOfMemory(
        override val code: String = "OUT_OF_MEMORY",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : SystemError() {
        override val recoverable: Boolean = true
    }

    data class InvalidState(
        override val code: String = "INVALID_STATE",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : SystemError() {
        override val recoverable: Boolean = false
    }

    data class UnknownError(
        override val code: String = "UNKNOWN_ERROR",
        override val message: String,
        val cause: Throwable? = null,
        override val timestamp: Instant = Instant.now()
    ) : SystemError() {
        override val recoverable: Boolean = false
    }
}

/**
 * 媒体相关错误
 */
sealed class MediaError : StreamError() {
    data class CameraError(
        override val code: String = "CAMERA_ERROR",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : MediaError() {
        override val recoverable: Boolean = true
    }

    data class AudioCaptureError(
        override val code: String = "AUDIO_CAPTURE_ERROR",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : MediaError() {
        override val recoverable: Boolean = true
    }

    data class ScreenCaptureError(
        override val code: String = "SCREEN_CAPTURE_ERROR",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : MediaError() {
        override val recoverable: Boolean = true
    }

    data class SurfaceError(
        override val code: String = "SURFACE_ERROR",
        override val message: String,
        override val timestamp: Instant = Instant.now()
    ) : MediaError() {
        override val recoverable: Boolean = true
    }
}