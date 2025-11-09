package com.astra.avpush.infrastructure.stream.nativebridge

import android.view.Surface

/**
 * Native bridge exposing sender operations implemented in C++.
 *
 * Kotlin only routes calls while the heavy work lives in the native layer.
 */
internal object NativeSenderBridge {

    init {
        System.loadLibrary("astra")
    }

    external fun nativeCreateSender(handle: Long, protocolOrdinal: Int)
    external fun nativeDestroySender(handle: Long)

    external fun nativeConnect(handle: Long, callback: NativeSenderCallbackProxy, url: String)
    external fun nativeClose(handle: Long)

    external fun nativeConfigureVideo(
        handle: Long,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        iframeInterval: Int,
        codecOrdinal: Int
    )

    external fun nativeConfigureAudioEncoder(
        handle: Long,
        sampleRate: Int,
        channels: Int,
        bitrateKbps: Int,
        bytesPerSample: Int
    )

    external fun nativeConfigureSession(
        handle: Long,
        sampleRate: Int,
        channels: Int,
        bytesPerSample: Int,
        audioBitrateKbps: Int,
        videoWidth: Int,
        videoHeight: Int,
        videoFps: Int,
        videoBitrateKbps: Int,
        iframeInterval: Int,
        codecOrdinal: Int
    )

    external fun nativePrepareVideoSurface(
        handle: Long,
        width: Int,
        height: Int,
        fps: Int,
        bitrateKbps: Int,
        iframeInterval: Int,
        codecOrdinal: Int
    ): Surface?

    external fun nativeReleaseVideoSurface(handle: Long)

    external fun nativeStartVideo(handle: Long)
    external fun nativeStopVideo(handle: Long)
    external fun nativeUpdateVideoBitrate(handle: Long, bitrateKbps: Int)

    external fun nativeStartAudio(handle: Long)
    external fun nativeStopAudio(handle: Long)

    external fun nativeStartSession(handle: Long)
    external fun nativePauseSession(handle: Long)
    external fun nativeResumeSession(handle: Long)
    external fun nativeStopSession(handle: Long)
    external fun nativeSetMute(handle: Long, muted: Boolean)
}
