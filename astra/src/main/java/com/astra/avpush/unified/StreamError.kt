package com.astra.avpush.unified

import java.time.Instant

sealed class StreamError protected constructor(
    val code: String,
    val recoverable: Boolean,
    val timestamp: Instant = Instant.now(),
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause) {

    companion object {
        fun from(throwable: Throwable): StreamError = when (throwable) {
            is StreamError -> throwable
            is SecurityException -> MediaError.CameraPermissionDenied(
                detail = throwable.message ?: "Permission denied"
            )
            is IllegalArgumentException -> ConfigurationError.InvalidParameter(
                parameter = "unknown",
                detail = throwable.message ?: "Invalid parameter"
            )
            is IllegalStateException -> SystemError.InvalidState(
                detail = throwable.message ?: "Invalid state"
            )
            else -> SystemError.UnknownError(
                detail = throwable.message ?: "Unknown error",
                underlying = throwable
            )
        }
    }
}

sealed class TransportError protected constructor(
    code: String,
    recoverable: Boolean,
    timestamp: Instant = Instant.now(),
    message: String,
    cause: Throwable? = null
) : StreamError(code, recoverable, timestamp, message, cause) {

    data class ConnectionFailed(
        val transport: TransportProtocol,
        val detail: String = "Transport connection failed",
        val error: Throwable? = null,
        val at: Instant = Instant.now()
    ) : TransportError(
        code = "TRANSPORT_CONNECTION_FAILED",
        recoverable = true,
        timestamp = at,
        message = detail,
        cause = error
    )

    data class AuthenticationFailed(
        val transport: TransportProtocol,
        val detail: String = "Authentication failed",
        val at: Instant = Instant.now()
    ) : TransportError(
        code = "TRANSPORT_AUTH_FAILED",
        recoverable = false,
        timestamp = at,
        message = detail
    )

    data class NetworkError(
        val transport: TransportProtocol,
        val errorCode: Int,
        val detail: String = "Network error",
        val at: Instant = Instant.now()
    ) : TransportError(
        code = "NETWORK_ERROR",
        recoverable = true,
        timestamp = at,
        message = "$detail (code=$errorCode)"
    )

    data class TimeoutError(
        val transport: TransportProtocol,
        val detail: String = "Transport timeout",
        val at: Instant = Instant.now()
    ) : TransportError(
        code = "TRANSPORT_TIMEOUT",
        recoverable = true,
        timestamp = at,
        message = detail
    )

    data class ProtocolError(
        val transport: TransportProtocol,
        val detail: String = "Protocol error",
        val error: Throwable? = null,
        val at: Instant = Instant.now()
    ) : TransportError(
        code = "PROTOCOL_ERROR",
        recoverable = false,
        timestamp = at,
        message = detail,
        cause = error
    )
}

sealed class EncodingError protected constructor(
    code: String,
    recoverable: Boolean,
    timestamp: Instant = Instant.now(),
    message: String,
    cause: Throwable? = null
) : StreamError(code, recoverable, timestamp, message, cause) {

    data class HardwareEncoderFailed(
        val detail: String = "Hardware encoder failure",
        val error: Throwable? = null,
        val at: Instant = Instant.now()
    ) : EncodingError(
        code = "HARDWARE_ENCODER_FAILED",
        recoverable = true,
        timestamp = at,
        message = detail,
        cause = error
    )

    data class SoftwareEncoderFailed(
        val detail: String = "Software encoder failure",
        val at: Instant = Instant.now()
    ) : EncodingError(
        code = "SOFTWARE_ENCODER_FAILED",
        recoverable = false,
        timestamp = at,
        message = detail
    )

    data class SettingsInvalid(
        val parameter: String,
        val detail: String = "Invalid encoder parameter",
        val at: Instant = Instant.now()
    ) : EncodingError(
        code = "ENCODING_CONFIG_ERROR",
        recoverable = false,
        timestamp = at,
        message = "$detail: $parameter"
    )

    data class BufferOverflow(
        val detail: String = "Encoding buffer overflow",
        val at: Instant = Instant.now()
    ) : EncodingError(
        code = "ENCODING_BUFFER_OVERFLOW",
        recoverable = true,
        timestamp = at,
        message = detail
    )

    data class FormatNotSupported(
        val format: String,
        val detail: String = "Format not supported",
        val at: Instant = Instant.now()
    ) : EncodingError(
        code = "FORMAT_NOT_SUPPORTED",
        recoverable = false,
        timestamp = at,
        message = "$detail: $format"
    )
}

sealed class ConfigurationError protected constructor(
    code: String,
    recoverable: Boolean,
    timestamp: Instant = Instant.now(),
    message: String
) : StreamError(code, recoverable, timestamp, message) {

    data class InvalidParameter(
        val parameter: String,
        val detail: String = "Invalid parameter",
        val at: Instant = Instant.now()
    ) : ConfigurationError(
        code = "INVALID_PARAMETER",
        recoverable = false,
        timestamp = at,
        message = "$detail: $parameter"
    )

    data class UnsupportedConfiguration(
        val detail: String = "Unsupported configuration",
        val at: Instant = Instant.now()
    ) : ConfigurationError(
        code = "UNSUPPORTED_CONFIGURATION",
        recoverable = false,
        timestamp = at,
        message = detail
    )

    data class ConflictingSettings(
        val conflictingParameters: List<String>,
        val detail: String = "Conflicting settings",
        val at: Instant = Instant.now()
    ) : ConfigurationError(
        code = "CONFLICTING_SETTINGS",
        recoverable = false,
        timestamp = at,
        message = "$detail: ${'$'}conflictingParameters"
    )
}

sealed class SystemError protected constructor(
    code: String,
    recoverable: Boolean,
    timestamp: Instant = Instant.now(),
    message: String,
    cause: Throwable? = null
) : StreamError(code, recoverable, timestamp, message, cause) {

    data class PermissionDenied(
        val detail: String = "Permission denied",
        val at: Instant = Instant.now()
    ) : SystemError(
        code = "PERMISSION_DENIED",
        recoverable = false,
        timestamp = at,
        message = detail
    )

    data class ResourceUnavailable(
        val resource: String,
        val detail: String = "Resource unavailable",
        val at: Instant = Instant.now()
    ) : SystemError(
        code = "RESOURCE_UNAVAILABLE",
        recoverable = true,
        timestamp = at,
        message = "$detail: $resource"
    )

    data class OutOfMemory(
        val detail: String = "Out of memory",
        val at: Instant = Instant.now()
    ) : SystemError(
        code = "OUT_OF_MEMORY",
        recoverable = true,
        timestamp = at,
        message = detail
    )

    data class InvalidState(
        val detail: String = "Invalid state",
        val at: Instant = Instant.now()
    ) : SystemError(
        code = "INVALID_STATE",
        recoverable = false,
        timestamp = at,
        message = detail
    )

    data class UnknownError(
        val detail: String = "Unknown error",
        val at: Instant = Instant.now(),
        val underlying: Throwable? = null
    ) : SystemError(
        code = "UNKNOWN_ERROR",
        recoverable = false,
        timestamp = at,
        message = detail,
        cause = underlying
    )
}

sealed class MediaError protected constructor(
    code: String,
    recoverable: Boolean,
    timestamp: Instant = Instant.now(),
    message: String,
    cause: Throwable? = null
) : StreamError(code, recoverable, timestamp, message, cause) {

    data class CameraUnavailable(
        val detail: String = "Camera unavailable",
        val at: Instant = Instant.now(),
        val underlying: Throwable? = null
    ) : MediaError(
        code = "CAMERA_UNAVAILABLE",
        recoverable = false,
        timestamp = at,
        message = detail,
        cause = underlying
    )

    data class CameraHardwareFailure(
        val detail: String = "Camera hardware failure",
        val at: Instant = Instant.now(),
        val underlying: Throwable? = null
    ) : MediaError(
        code = "CAMERA_HARDWARE_FAILURE",
        recoverable = true,
        timestamp = at,
        message = detail,
        cause = underlying
    )

    data class CameraPermissionDenied(
        val detail: String = "Camera permission denied",
        val at: Instant = Instant.now()
    ) : MediaError(
        code = "CAMERA_PERMISSION_DENIED",
        recoverable = false,
        timestamp = at,
        message = detail
    )

    data class AudioCaptureError(
        val detail: String = "Audio capture error",
        val at: Instant = Instant.now(),
        val underlying: Throwable? = null
    ) : MediaError(
        code = "AUDIO_CAPTURE_ERROR",
        recoverable = true,
        timestamp = at,
        message = detail,
        cause = underlying
    )

    data class SurfaceError(
        val detail: String = "Surface error",
        val at: Instant = Instant.now()
    ) : MediaError(
        code = "SURFACE_ERROR",
        recoverable = true,
        timestamp = at,
        message = detail
    )
}
